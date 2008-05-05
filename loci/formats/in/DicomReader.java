//
// DicomReader.java
//

/*
LOCI Bio-Formats package for reading and converting biological file formats.
Copyright (C) 2005-@year@ Melissa Linkert, Curtis Rueden, Chris Allan,
Eric Kjellman and Brian Loranger.

This program is free software; you can redistribute it and/or modify
it under the terms of the GNU Library General Public License as published by
the Free Software Foundation; either version 2 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU Library General Public License for more details.

You should have received a copy of the GNU Library General Public License
along with this program; if not, write to the Free Software
Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
*/

package loci.formats.in;

import java.io.*;
import java.text.*;
import java.util.*;
import loci.formats.*;
import loci.formats.codec.*;
import loci.formats.meta.FilterMetadata;
import loci.formats.meta.MetadataStore;

/**
 * DicomReader is the file format reader for DICOM files.
 * Much of this code is adapted from ImageJ's DICOM reader; see
 * http://rsb.info.nih.gov/ij/developer/source/ij/plugin/DICOM.java.html
 *
 * <dl><dt><b>Source code:</b></dt>
 * <dd><a href="https://skyking.microscopy.wisc.edu/trac/java/browser/trunk/loci/formats/in/DicomReader.java">Trac</a>,
 * <a href="https://skyking.microscopy.wisc.edu/svn/java/trunk/loci/formats/in/DicomReader.java">SVN</a></dd></dl>
 */
public class DicomReader extends FormatReader {

  // -- Constants --

  private static final String[] DICOM_SUFFIXES = {
    "dic", "dcm", "dicom", "j2ki", "j2kr"
  };

  private static final Hashtable TYPES = buildTypes();

  private static final int PIXEL_REPRESENTATION = 0x00280103;
  private static final int TRANSFER_SYNTAX_UID = 0x00020010;
  private static final int SLICE_SPACING = 0x00180088;
  private static final int SAMPLES_PER_PIXEL = 0x00280002;
  private static final int PHOTOMETRIC_INTERPRETATION = 0x00280004;
  private static final int PLANAR_CONFIGURATION = 0x00280006;
  private static final int NUMBER_OF_FRAMES = 0x00280008;
  private static final int ROWS = 0x00280010;
  private static final int COLUMNS = 0x00280011;
  private static final int PIXEL_SPACING = 0x00280030;
  private static final int BITS_ALLOCATED = 0x00280100;
  private static final int WINDOW_CENTER = 0x00281050;
  private static final int WINDOW_WIDTH = 0x00281051;
  private static final int RESCALE_INTERCEPT = 0x00281052;
  private static final int RESCALE_SLOPE = 0x00281053;
  private static final int ICON_IMAGE_SEQUENCE = 0x00880200;
  private static final int ITEM = 0xFFFEE000;
  private static final int ITEM_DELIMINATION = 0xFFFEE00D;
  private static final int SEQUENCE_DELIMINATION = 0xFFFEE0DD;
  private static final int PIXEL_DATA = 0x7FE00010;

  private static final int AE = 0x4145, AS = 0x4153, AT = 0x4154, CS = 0x4353;
  private static final int DA = 0x4441, DS = 0x4453, DT = 0x4454, FD = 0x4644;
  private static final int FL = 0x464C, IS = 0x4953, LO = 0x4C4F, LT = 0x4C54;
  private static final int PN = 0x504E, SH = 0x5348, SL = 0x534C, SS = 0x5353;
  private static final int ST = 0x5354, TM = 0x544D, UI = 0x5549, UL = 0x554C;
  private static final int US = 0x5553, UT = 0x5554, OB = 0x4F42, OW = 0x4F57;
  private static final int SQ = 0x5351, UN = 0x554E, QQ = 0x3F3F;

  private static final int IMPLICIT_VR = 0x2d2d;

  // -- Fields --

  /** Bits per pixel. */
  private int bitsPerPixel;

  private int location;
  private int elementLength;
  private int vr;
  private boolean oddLocations;
  private boolean inSequence;
  private boolean bigEndianTransferSyntax;
  private byte[][] lut;
  private short[][] shortLut;
  private long[] offsets;
  private int maxPixelValue;

  private boolean isJP2K = false;
  private boolean isJPEG = false;
  private boolean isRLE = false;
  private boolean inverted;

  private String date, time, imageType;

  // -- Constructor --

  /** Constructs a new DICOM reader. */
  // "Digital Imaging and Communications in Medicine" is nasty long.
  public DicomReader() {
    super("Digital Img. & Comm. in Med.",
      new String[] {"dic", "dcm", "dicom", "jp2", "j2ki", "j2kr", "raw"});
    blockCheckLen = 132;
    // FIXME: Would like to enable extensionless DICOM support with
    // suffixNecessary = false here, but in order to do that, the
    // isThisType(byte[]) method must robustly identify DICOM files
    // from their headers.
    //suffixNecessary = false;
    suffixSufficient = false;
  }

  // -- IFormatReader API methods --

  /* @see loci.formats.IFormatReader#isThisType(String, boolean) */
  public boolean isThisType(String name, boolean open) {
    // extension is sufficient as long as it is DIC, DCM, DICOM, J2KI, or J2KR
    if (checkSuffix(name, DICOM_SUFFIXES)) return true;
    return super.isThisType(name, open);
  }

  /* @see loci.formats.IFormatReader#isThisType(byte[]) */
  public boolean isThisType(byte[] block) {
    if (block.length < blockCheckLen) return false;
    return new String(block, 0, blockCheckLen).indexOf("DICM") >= 0;
  }

  /* @see loci.formats.IFormatReader#get8BitLookupTable() */
  public byte[][] get8BitLookupTable() {
    FormatTools.assertId(currentId, true, 1);
    return lut;
  }

  /* @see loci.formats.IFormatReader#get16BitLookupTable() */
  public short[][] get16BitLookupTable() {
    FormatTools.assertId(currentId, true, 1);
    return shortLut;
  }

  /**
   * @see loci.formats.IFormatReader#openBytes(int, byte[], int, int, int, int)
   */
  public byte[] openBytes(int no, byte[] buf, int x, int y, int w, int h)
    throws FormatException, IOException
  {
    FormatTools.assertId(currentId, true, 1);
    FormatTools.checkPlaneNumber(this, no);
    FormatTools.checkBufferSize(this, buf.length, w, h);

    int bpp = FormatTools.getBytesPerPixel(core.pixelType[0]);
    int bytes = core.sizeX[0] * core.sizeY[0] * bpp *
      (isIndexed() ? 1 : core.sizeC[0]);
    in.seek(offsets[no]);

    if (isRLE) {
      // plane is compressed using run-length encoding
      byte[] b = new byte[bytes];
      in.read(b);
      PackbitsCodec codec = new PackbitsCodec();
      byte[] t = codec.decompress(b, new Integer(bytes));
      if (t.length < bytes) {
        byte[] tmp = t;
        t = new byte[bytes];
        System.arraycopy(tmp, 0, t, 0, tmp.length);
      }

      if (bpp > 1) {
        int plane = bytes / bpp;
        byte[][] tmp = new byte[bpp][plane];
        for (int i=0; i<bpp; i++) {
          System.arraycopy(t, i*plane, tmp[i], 0, plane);
        }
        for (int i=0; i<plane; i++) {
          for (int j=0; j<bpp; j++) {
            t[i*bpp + j] =
              core.littleEndian[0] ? tmp[bpp - j - 1][i] : tmp[j][i];
          }
        }
      }

      int rowLen = w * bpp;
      int srcRowLen = core.sizeX[0] * bpp;

      int ec = isIndexed() ? 1 : core.sizeC[0];
      int srcPlane = core.sizeY[0] * srcRowLen;

      for (int c=0; c<ec; c++) {
        for (int row=0; row<h; row++) {
          int src = c * srcPlane + (row + y) * srcRowLen + x * bpp;
          int dest = h * rowLen * c + row * rowLen;
          int len = (int) Math.min(rowLen, t.length - src - 1);
          if (len < 0) break;
          System.arraycopy(t, src, buf, dest, len);
        }
      }
    }
    else if (isJPEG || isJP2K) {
      // plane is compressed using JPEG or JPEG-2000
      byte[] b = new byte[(int) (in.length() - in.getFilePointer())];
      in.read(b);
      if (b[2] != (byte) 0xff) {
        byte[] tmp = new byte[b.length + 1];
        tmp[0] = b[0];
        tmp[1] = b[1];
        tmp[2] = (byte) 0xff;
        System.arraycopy(b, 2, tmp, 3, b.length - 2);
        b = tmp;
      }
      Codec codec = null;
      if (isJPEG) codec = new JPEGCodec();
      else codec = new JPEG2000Codec();
      b = codec.decompress(b, new Object[] {new Boolean(core.littleEndian[0]),
        new Boolean(core.interleaved[0])});

      int rowLen = w * bpp;
      int srcRowLen = core.sizeX[0] * bpp;

      int ec = isIndexed() ? 1 : core.sizeC[0];
      int srcPlane = core.sizeY[0] * srcRowLen;

      for (int c=0; c<ec; c++) {
        for (int row=0; row<h; row++) {
          System.arraycopy(b, c * srcPlane + (row + y) * srcRowLen + x * bpp,
            buf, h * rowLen * c + row * rowLen, rowLen);
        }
      }
    }
    else {
      // plane is not compressed
      in.skipBytes(4);
      byte b1 = in.readByte();
      byte b2 = in.readByte();
      if ((b1 == 0x7f && b2 == (byte) 0xe0) ||
        (b1 == (byte) 0xe0 && b2 == 0x7f))
      {
        in.skipBytes(10);
        for (int i=0; i<offsets.length; i++) {
          offsets[i] += 16;
        }
      }
      else in.seek(in.getFilePointer() - 6);

      int c = isIndexed() ? 1 : core.sizeC[0];
      in.skipBytes(y * c * bpp * core.sizeX[0]);
      for (int row=0; row<h; row++) {
        in.skipBytes(x * c * bpp);
        in.read(buf, row * w * c * bpp, w * c * bpp);
        in.skipBytes(c * bpp * (core.sizeX[0] - w - x));
      }
    }

    if (inverted) {
      // pixels are stored such that white -> 0; invert the values so that
      // white -> 255 (or 65535)
      if (bpp == 1) {
        for (int i=0; i<buf.length; i++) {
          buf[i] = (byte) (255 - buf[i]);
        }
      }
      else if (bpp == 2) {
        if (maxPixelValue == -1) maxPixelValue = 65535;
        for (int i=0; i<buf.length; i+=2) {
          short s = DataTools.bytesToShort(buf, i, 2, core.littleEndian[0]);
          s = (short) (maxPixelValue - s);
          if (core.littleEndian[0]) {
            buf[i + 1] = (byte) (s >> 8);
            buf[i] = (byte) (s & 0xff);
          }
          else {
            buf[i] = (byte) (s >> 8);
            buf[i + 1] = (byte) (s & 0xff);
          }
        }
      }
    }

    return buf;
  }

  // -- IFormatHandler API methods --

  /* @see loci.formats.IFormatHandler#close() */
  public void close() throws IOException {
    super.close();
    bitsPerPixel = location = elementLength = vr = 0;
    oddLocations = inSequence = bigEndianTransferSyntax = false;
    isJPEG = isRLE = false;
    lut = null;
    offsets = null;
    shortLut = null;
  }

  // -- Internal FormatReader API methods --

  /* @see loci.formats.FormatReader#initFile(String) */
  protected void initFile(String id) throws FormatException, IOException {
    if (debug) debug("DicomReader.initFile(" + id + ")");
    super.initFile(id);
    in = new RandomAccessStream(id);
    in.order(true);

    core.littleEndian[0] = true;
    location = 0;
    isJPEG = false;
    isRLE = false;
    bigEndianTransferSyntax = false;
    oddLocations = false;
    inSequence = false;
    bitsPerPixel = 0;
    elementLength = 0;
    vr = 0;
    lut = null;
    offsets = null;
    inverted = false;

    // some DICOM files have a 128 byte header followed by a 4 byte identifier

    status("Verifying DICOM format");

    in.seek(128);
    if (in.readString(4).equals("DICM")) {
      // header exists, so we'll read it
      in.seek(0);
      addMeta("Header information", in.readString(128));
      in.readInt();
      location = 128;
    }
    else in.seek(0);

    status("Reading tags");

    long baseOffset = 0;

    boolean decodingTags = true;
    boolean signed = false;

    while (decodingTags) {
      if (in.getFilePointer() + 2 >= in.length()) {
        break;
      }
      int tag = getNextTag();

      if (elementLength == 0) continue;

      oddLocations = (location & 1) != 0;

      String s;
      switch (tag) {
        case TRANSFER_SYNTAX_UID:
          // this tag can indicate which compression scheme is used
          s = in.readString(elementLength);
          addInfo(tag, s);
          if (s.startsWith("1.2.840.10008.1.2.4.9")) isJP2K = true;
          else if (s.startsWith("1.2.840.10008.1.2.4")) isJPEG = true;
          else if (s.startsWith("1.2.840.10008.1.2.5")) isRLE = true;
          else if (s.indexOf("1.2.4") > -1 || s.indexOf("1.2.5") > -1) {
            throw new FormatException("Sorry, compressed DICOM images not " +
              "supported");
          }
          if (s.indexOf("1.2.840.10008.1.2.2") >= 0) {
            bigEndianTransferSyntax = true;
          }
          break;
        case NUMBER_OF_FRAMES:
          s = in.readString(elementLength);
          addInfo(tag, s);
          double frames = Double.parseDouble(s);
          if (frames > 1.0) core.imageCount[0] = (int) frames;
          break;
        case SAMPLES_PER_PIXEL:
          addInfo(tag, in.readShort());
          break;
        case PHOTOMETRIC_INTERPRETATION:
          addInfo(tag, in.readString(elementLength));
          break;
        case PLANAR_CONFIGURATION:
          int config = in.readShort();
          core.interleaved[0] = config == 0;
          addInfo(tag, config);
          break;
        case ROWS:
          if (core.sizeY[0] == 0) core.sizeY[0] = in.readShort();
          else in.skipBytes(2);
          addInfo(tag, core.sizeY[0]);
          break;
        case COLUMNS:
          if (core.sizeX[0] == 0) core.sizeX[0] = in.readShort();
          else in.skipBytes(2);
          addInfo(tag, core.sizeX[0]);
          break;
        case PIXEL_SPACING:
          addInfo(tag, in.readString(elementLength));
          break;
        case SLICE_SPACING:
          addInfo(tag, in.readString(elementLength));
          break;
        case BITS_ALLOCATED:
          if (bitsPerPixel == 0) bitsPerPixel = in.readShort();
          else in.skipBytes(2);
          addInfo(tag, bitsPerPixel);
          break;
        case PIXEL_REPRESENTATION:
          short ss = in.readShort();
          signed = ss == 1;
          addInfo(tag, ss);
          break;
        case RESCALE_INTERCEPT:
          addInfo(tag, in.readString(elementLength));
          break;
        case 537262910:
        case WINDOW_WIDTH:
          String t = in.readString(elementLength);
          if (t.trim().length() == 0) maxPixelValue = -1;
          else {
            try {
              maxPixelValue = new Double(t.trim()).intValue();
            }
            catch (NumberFormatException e) {
              maxPixelValue = -1;
            }
          }
          addInfo(tag, t);
          break;
        case WINDOW_CENTER:
        case RESCALE_SLOPE:
          addInfo(tag, in.readString(elementLength));
          break;
        case PIXEL_DATA:
        case 0x7fe00000:
        case 0xfffee000:
          if (elementLength != 0) {
            baseOffset = in.getFilePointer();
            addInfo(tag, location);
            decodingTags = false;
          }
          else addInfo(tag, null);
          break;
        case 0x7f880010:
          if (elementLength != 0) {
            baseOffset = location + 4;
            decodingTags = false;
          }
          break;
        default:
          s = in.readString(elementLength);
          addInfo(tag, s);
      }
      if (in.getFilePointer() >= (in.length() - 4)) {
        decodingTags = false;
      }
    }
    if (core.imageCount[0] == 0) core.imageCount[0] = 1;

    int plane = core.sizeX[0] * core.sizeY[0] *
      (lut == null ? core.sizeC[0] : 1) *
      FormatTools.getBytesPerPixel(core.pixelType[0]);

    status("Calculating image offsets");

    // calculate the offset to each plane

    offsets = new long[core.imageCount[0]];
    for (int i=0; i<core.imageCount[0]; i++) {
      if (isRLE) {
        if (i == 0) in.seek(baseOffset);
        else {
          in.seek(offsets[i - 1]);
          new PackbitsCodec().decompress(in, new Integer(plane));
        }
        in.skipBytes(i == 0 ? 78 : 67);
        while (in.read() == 0);
        offsets[i] = in.getFilePointer() - 1;
      }
      else if (isJPEG || isJP2K) {
        // scan for next JPEG magic byte sequence
        if (i == 0) offsets[i] = baseOffset;
        else offsets[i] = offsets[i - 1] + 3;

        byte secondCheck = isJPEG ? (byte) 0xd8 : (byte) 0x4f;

        in.seek(offsets[i]);
        byte[] buf = new byte[8192];
        in.read(buf);
        boolean found = false;
        while (!found) {
          for (int q=0; q<buf.length-3; q++) {
            if (buf[q] == (byte) 0xff && buf[q + 1] == secondCheck) {
              if (isJPEG ||
                (isJP2K && buf[q + 2] == (byte) 0xff && buf[q + 3] == 0x51))
              {
                found = true;
                offsets[i] = in.getFilePointer() + q - buf.length;
                break;
              }
            }
          }
          if (!found) {
            for (int q=0; q<4; q++) {
              buf[q] = buf[buf.length + q - 4];
            }
            in.read(buf, 4, buf.length - 4);
          }
        }
      }
      else offsets[i] = baseOffset + plane*i;
    }

    status("Populating metadata");

    core.sizeZ[0] = core.imageCount[0];
    if (core.sizeC[0] == 0) core.sizeC[0] = 1;
    core.rgb[0] = core.sizeC[0] > 1;
    core.sizeT[0] = 1;
    core.currentOrder[0] = "XYCZT";
    core.metadataComplete[0] = true;
    core.falseColor[0] = false;
    if (isRLE) core.interleaved[0] = false;

    // The metadata store we're working with.
    MetadataStore store =
      new FilterMetadata(getMetadataStore(), isMetadataFiltered());
    store.setImageName("", 0);

    while (bitsPerPixel % 8 != 0) bitsPerPixel++;
    if (bitsPerPixel == 24 || bitsPerPixel == 48) bitsPerPixel /= 3;

    switch (bitsPerPixel) {
      case 8:
        core.pixelType[0] = signed ? FormatTools.INT8 : FormatTools.UINT8;
        break;
      case 16:
        core.pixelType[0] = signed ? FormatTools.INT16 : FormatTools.UINT16;
        break;
      case 32:
        core.pixelType[0] = signed ? FormatTools.INT32 : FormatTools.UINT32;
        break;
    }

    // populate OME-XML node
    MetadataTools.populatePixels(store, this);

    String stamp = null;

    if (date != null && time != null) {
      stamp = date + " " + time;
      SimpleDateFormat parse =
        new SimpleDateFormat("yyyy.MM.dd HH:mm:ss.SSSSSS");
      Date d = parse.parse(stamp, new ParsePosition(0));
      SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
      if (d != null) stamp = fmt.format(d);
      else stamp = null;
    }

    if (stamp == null || stamp.trim().equals("")) stamp = null;

    store.setImageCreationDate(stamp, 0);
    store.setImageDescription(imageType, 0);

    // CTR CHECK
//    store.setInstrumentManufacturer((String) getMeta("Manufacturer"), 0);
//    store.setInstrumentModel((String) getMeta("Manufacturer's Model Name"), 0);

    // CTR CHECK
//    for (int i=0; i<core.sizeC[0]; i++) {
//      store.setLogicalChannel(i, null, null, null, null, null, null, null, null,
//       null, null, null, null, null, null, null, null, null, null, null, null,
//       null, null, null, null);
//    }
  }

  // -- Helper methods --

  private void addInfo(int tag, String value) throws IOException {
    long oldFp = in.getFilePointer();
    String oldValue = value;
    String info = getHeaderInfo(tag, value);

    if (info != null && tag != ITEM) {
      if (info.trim().equals("")) info = oldValue;

      String key = (String) TYPES.get(new Integer(tag));
      if (key == null) key = "" + tag;
      if (key.equals("Samples per pixel")) {
        core.sizeC[0] = Integer.parseInt(info.trim());
        if (core.sizeC[0] > 1) core.rgb[0] = true;
      }
      else if (key.equals("Photometric Interpretation")) {
        if (info.trim().equals("PALETTE COLOR")) {
          core.indexed[0] = true;
          core.sizeC[0] = 1;
          core.rgb[0] = false;
          lut = new byte[3][];
        }
        else if (info.trim().startsWith("MONOCHROME")) {
          inverted = info.trim().endsWith("1");
        }
      }
      else if (key.indexOf("Palette Color LUT Data") != -1) {
        String color = key.substring(0, key.indexOf(" ")).trim();
        int ndx = color.equals("Red") ? 0 : color.equals("Green") ? 1 : 2;
        long fp = in.getFilePointer();
        in.seek(in.getFilePointer() - elementLength + 1);
        lut[ndx] = new byte[elementLength / 2];
        for (int i=0; i<lut[ndx].length; i++) {
          lut[ndx][i] = (byte) (in.read() & 0xff);
          in.skipBytes(1);
        }
        in.seek(fp);
      }
      else if (key.equals("Content Time")) time = info;
      else if (key.equals("Content Date")) date = info;
      else if (key.equals("Image Type")) imageType = info;

      if (((tag & 0xffff0000) >> 16) != 0x7fe0) {
        addMeta(key, info);
      }
    }
  }

  private void addInfo(int tag, int value) throws IOException {
    addInfo(tag, Integer.toString(value));
  }

  private String getHeaderInfo(int tag, String value) throws IOException {
    if (tag == ITEM_DELIMINATION || tag == SEQUENCE_DELIMINATION) {
      inSequence = false;
    }

    Integer key = new Integer(tag);
    String id = (String) TYPES.get(key);

    if (id != null) {
      if (vr == IMPLICIT_VR && id != null) {
        vr = (id.charAt(0) << 8) + id.charAt(1);
      }
      if (id.length() > 2) id = id.substring(2);
    }

    if (tag == ITEM) return id != null ? id : null;
    if (value != null) return value;

    boolean skip = false;
    switch (vr) {
      case AE:
      case AS:
      case AT:
      case CS:
      case DA:
      case DS:
      case DT:
      case IS:
      case LO:
      case LT:
      case PN:
      case SH:
      case ST:
      case TM:
      case UI:
        value = in.readString(elementLength);
        break;
      case US:
        if (elementLength == 2) value = Integer.toString(in.readShort());
        else {
          value = "";
          int n = elementLength / 2;
          for (int i=0; i<n; i++) {
            value += Integer.toString(in.readShort()) + " ";
          }
        }
        break;
      case IMPLICIT_VR:
        value = in.readString(elementLength);
        if (elementLength <= 4 || elementLength > 44) value = null;
        break;
      case SQ:
        value = "";
        boolean privateTag = ((tag >> 16) & 1) != 0;
        if (tag == ICON_IMAGE_SEQUENCE || privateTag) skip = true;
        break;
      default:
        skip = true;
    }
    if (skip) {
      long skipCount = (long) elementLength;
      if (in.getFilePointer() + skipCount <= in.length()) {
        in.skipBytes((int) skipCount);
      }
      location += elementLength;
      value = "";
    }

    if (value != null && id == null && !value.equals("")) return value;
    else if (id == null) return null;
    else return value;
  }

  private int getLength(int tag) throws IOException {
    byte[] b = new byte[4];
    in.read(b);

    // We cannot know whether the VR is implicit or explicit
    // without the full DICOM Data Dictionary for public and
    // private groups.

    // We will assume the VR is explicit if the two bytes
    // match the known codes. It is possible that these two
    // bytes are part of a 32-bit length for an implicit VR.

    vr = (b[0] << 8) + b[1];
    switch (vr) {
      case OB:
      case OW:
      case SQ:
      case UN:
        // Explicit VR with 32-bit length if other two bytes are zero
        if ((b[2] == 0) || (b[3] == 0)) {
          return in.readInt();
        }
        vr = IMPLICIT_VR;
        return DataTools.bytesToInt(b, core.littleEndian[0]);
      case AE:
      case AS:
      case AT:
      case CS:
      case DA:
      case DS:
      case DT:
      case FD:
      case FL:
      case IS:
      case LO:
      case LT:
      case PN:
      case SH:
      case SL:
      case SS:
      case ST:
      case TM:
      case UI:
      case UL:
      case US:
      case UT:
      case QQ:
        // Explicit VR with 16-bit length
      	if (tag == 0x00283006) {
    	 	  return DataTools.bytesToInt(b, 2, 2, core.littleEndian[0]);
        }
        int n1 = DataTools.bytesToShort(b, 2, 2, core.littleEndian[0]);
        int n2 = DataTools.bytesToShort(b, 2, 2, !core.littleEndian[0]);
        return (int) Math.min(n1, n2);
      default:
        vr = IMPLICIT_VR;
        return DataTools.bytesToInt(b, core.littleEndian[0]);
    }
  }

  private int getNextTag() throws IOException {
    int groupWord = in.readShort();
    if (groupWord == 0x0800 && bigEndianTransferSyntax) {
      core.littleEndian[0] = false;
      groupWord = 0x0008;
      in.order(false);
    }

    int elementWord = in.readShort();
    int tag = ((groupWord << 16) & 0xffff0000) | (elementWord & 0xffff);

    elementLength = getLength(tag);

    if (elementLength == 0 && groupWord == 0x7fe0) {
      elementLength = getLength(tag);
    }

    // HACK - needed to read some GE files
    // The element length must be even!
    if (elementLength == 13 && !oddLocations) elementLength = 10;

    // "Undefined" element length.
    // This is a sort of bracket that encloses a sequence of elements.
    if (elementLength == -1) {
      elementLength = 0;
      inSequence = true;
    }
    return tag;
  }

  // -- Utility methods --

  /**
   * Assemble the data dictionary.
   * This is incomplete at best, since there are literally thousands of
   * fields defined by the DICOM specifications.
   */
  private static Hashtable buildTypes() {
    Hashtable dict = new Hashtable();

    dict.put(new Integer(0x00020002), "Media Storage SOP Class UID");
    dict.put(new Integer(0x00020003), "Media Storage SOP Instance UID");
    dict.put(new Integer(0x00020010), "Transfer Syntax UID");
    dict.put(new Integer(0x00020012), "Implementation Class UID");
    dict.put(new Integer(0x00020013), "Implementation Version Name");
    dict.put(new Integer(0x00020016), "Source Application Entity Title");
    dict.put(new Integer(0x00080005), "Specific Character Set");
    dict.put(new Integer(0x00080008), "Image Type");
    dict.put(new Integer(0x00080010), "Recognition Code");
    dict.put(new Integer(0x00080012), "Instance Creation Date");
    dict.put(new Integer(0x00080013), "Instance Creation Time");
    dict.put(new Integer(0x00080014), "Instance Creator UID");
    dict.put(new Integer(0x00080016), "SOP Class UID");
    dict.put(new Integer(0x00080018), "SOP Instance UID");
    dict.put(new Integer(0x0008001a), "Related General SOP Class UID");
    dict.put(new Integer(0x0008001b), "Original Specialized SOP Class UID");
    dict.put(new Integer(0x00080020), "Study Date");
    dict.put(new Integer(0x00080021), "Series Date");
    dict.put(new Integer(0x00080022), "Acquisition Date");
    dict.put(new Integer(0x00080023), "Content Date");
    dict.put(new Integer(0x00080024), "Overlay Date");
    dict.put(new Integer(0x00080025), "Curve Date");
    dict.put(new Integer(0x0008002a), "Acquisition Date/Time");
    dict.put(new Integer(0x00080030), "Study Time");
    dict.put(new Integer(0x00080031), "Series Time");
    dict.put(new Integer(0x00080032), "Acquisition Time");
    dict.put(new Integer(0x00080033), "Content Time");
    dict.put(new Integer(0x00080034), "Overlay Time");
    dict.put(new Integer(0x00080035), "Curve Time");
    dict.put(new Integer(0x00080041), "Data Set Subtype");
    dict.put(new Integer(0x00080050), "Accession Number");
    dict.put(new Integer(0x00080052), "Query/Retrieve Level");
    dict.put(new Integer(0x00080054), "Retrieve AE Title");
    dict.put(new Integer(0x00080056), "Instance Availability");
    dict.put(new Integer(0x00080058), "Failed SOP Instance UID List");
    dict.put(new Integer(0x00080060), "Modality");
    dict.put(new Integer(0x00080061), "Modalities in Study");
    dict.put(new Integer(0x00080062), "SOP Classes in Study");
    dict.put(new Integer(0x00080064), "Conversion Type");
    dict.put(new Integer(0x00080068), "Presentation Intent Type");
    dict.put(new Integer(0x00080070), "Manufacturer");
    dict.put(new Integer(0x00080080), "Institution Name");
    dict.put(new Integer(0x00080081), "Institution Address");
    dict.put(new Integer(0x00080082), "Institution Code Sequence");
    dict.put(new Integer(0x00080090), "Referring Physician's Name");
    dict.put(new Integer(0x00080092), "Referring Physician's Address");
    dict.put(new Integer(0x00080094), "Referring Physician's Telephone");
    dict.put(new Integer(0x00080096), "Referring Physician ID");
    dict.put(new Integer(0x00080100), "Code Value");
    dict.put(new Integer(0x00080102), "Coding Scheme Designator");
    dict.put(new Integer(0x00080103), "Coding Scheme Version");
    dict.put(new Integer(0x00080104), "Code Meaning");
    dict.put(new Integer(0x00080105), "Mapping Resource");
    dict.put(new Integer(0x00080106), "Context Group Version");
    dict.put(new Integer(0x00080107), "Context Group Local Version");
    dict.put(new Integer(0x0008010b), "Context Group Extension Flag");
    dict.put(new Integer(0x0008010c), "Coding Scheme UID");
    dict.put(new Integer(0x0008010d), "Context Group Extension Creator UID");
    dict.put(new Integer(0x0008010f), "Context ID");
    dict.put(new Integer(0x00080110), "Coding Scheme ID");
    dict.put(new Integer(0x00080112), "Coding Scheme Registry");
    dict.put(new Integer(0x00080114), "Coding Scheme External ID");
    dict.put(new Integer(0x00080115), "Coding Scheme Name");
    dict.put(new Integer(0x00080116), "Responsible Organization");
    dict.put(new Integer(0x00080201), "Timezone Offset from UTC");
    dict.put(new Integer(0x00081010), "Station Name");
    dict.put(new Integer(0x00081030), "Study Description");
    dict.put(new Integer(0x00081032), "Procedure Code Sequence");
    dict.put(new Integer(0x0008103e), "Series Description");
    dict.put(new Integer(0x00081040), "Institutional Department Name");
    dict.put(new Integer(0x00081048), "Physician(s) of Record");
    dict.put(new Integer(0x00081049), "Physician(s) of Record ID");
    dict.put(new Integer(0x00081050), "Performing Physician's Name");
    dict.put(new Integer(0x00081052), "Performing Physican ID");
    dict.put(new Integer(0x00081060), "Name of Physician(s) Reading Study");
    dict.put(new Integer(0x00081062), "Physician(s) Reading Study ID");
    dict.put(new Integer(0x00081070), "Operator's Name");
    dict.put(new Integer(0x00081072), "Operator ID");
    dict.put(new Integer(0x00081080), "Admitting Diagnoses Description");
    dict.put(new Integer(0x00081084), "Admitting Diagnoses Code Sequence");
    dict.put(new Integer(0x00081090), "Manufacturer's Model Name");
    dict.put(new Integer(0x00081100), "Referenced Results Sequence");
    dict.put(new Integer(0x00081110), "Referenced Study Sequence");
    dict.put(new Integer(0x00081111), "Referenced Performed Procedure Step");
    dict.put(new Integer(0x00081115), "Referenced Series Sequence");
    dict.put(new Integer(0x00081120), "Referenced Patient Sequence");
    dict.put(new Integer(0x00081125), "Referenced Visit Sequence");
    dict.put(new Integer(0x00081130), "Referenced Overlay Sequence");
    dict.put(new Integer(0x0008113a), "Referenced Waveform Sequence");
    dict.put(new Integer(0x00081140), "Referenced Image Sequence");
    dict.put(new Integer(0x00081145), "Referenced Curve Sequence");
    dict.put(new Integer(0x0008114a), "Referenced Instance Sequence");
    dict.put(new Integer(0x00081150), "Referenced SOP Class UID");
    dict.put(new Integer(0x00081155), "Referenced SOP Instance UID");
    dict.put(new Integer(0x0008115a), "SOP Classes Supported");
    dict.put(new Integer(0x00081160), "Referenced Frame Number");
    dict.put(new Integer(0x00081195), "Transaction UID");
    dict.put(new Integer(0x00081197), "Failure Reason");
    dict.put(new Integer(0x00081198), "Failed SOP Sequence");
    dict.put(new Integer(0x00081199), "Referenced SOP Sequence");
    dict.put(new Integer(0x00081200),
      "Studies Containing Other Referenced Instances Sequence");
    dict.put(new Integer(0x00081250), "Related Series Sequence");
    dict.put(new Integer(0x00082111), "Derivation Description");
    dict.put(new Integer(0x00082112), "Source Image Sequence");
    dict.put(new Integer(0x00082120), "Stage Name");
    dict.put(new Integer(0x00082122), "Stage Number");
    dict.put(new Integer(0x00082124), "Number of Stages");
    dict.put(new Integer(0x00082127), "View Name");
    dict.put(new Integer(0x00082128), "View Number");
    dict.put(new Integer(0x00082129), "Number of Event Timers");
    dict.put(new Integer(0x0008212a), "Number of Views in Stage");
    dict.put(new Integer(0x00082130), "Event Elapsed Time(s)");
    dict.put(new Integer(0x00082132), "Event Timer Name(s)");
    dict.put(new Integer(0x00082142), "Start Trim");
    dict.put(new Integer(0x00082143), "Stop Trim");
    dict.put(new Integer(0x00082144), "Recommended Display Frame Rate");
    dict.put(new Integer(0x00082218), "Anatomic Region Sequence");
    dict.put(new Integer(0x00082220), "Anatomic Region Modifier Sequence");
    dict.put(new Integer(0x00082228), "Primary Anatomic Structure Sequence");
    dict.put(new Integer(0x00082229), "Anatomic Structure Sequence");
    dict.put(new Integer(0x00082230), "Primary Anatomic Structure Modifier");
    dict.put(new Integer(0x00082240), "Transducer Position Sequence");
    dict.put(new Integer(0x00082242), "Transducer Position Modifier Sequence");
    dict.put(new Integer(0x00082244), "Transducer Orientation Sequence");
    dict.put(new Integer(0x00082246), "Transducer Orientation Modifier");
    dict.put(new Integer(0x00083001), "Alternate Representation Sequence");
    dict.put(new Integer(0x00089007), "Frame Type");
    dict.put(new Integer(0x00089092), "Referenced Image Evidence Sequence");
    dict.put(new Integer(0x00089121), "Referenced Raw Data Sequence");
    dict.put(new Integer(0x00089123), "Creator-Version UID");
    dict.put(new Integer(0x00089124), "Derivation Image Sequence");
    dict.put(new Integer(0x00089154), "Source Image Evidence Sequence");
    dict.put(new Integer(0x00089205), "Pixel Representation");
    dict.put(new Integer(0x00089206), "Volumetric Properties");
    dict.put(new Integer(0x00089207), "Volume Based Calculation Technique");
    dict.put(new Integer(0x00089208), "Complex Image Component");
    dict.put(new Integer(0x00089209), "Acquisition Contrast");
    dict.put(new Integer(0x00089215), "Derivation Code Sequence");
    dict.put(new Integer(0x00089237),
      "Reference Grayscale Presentation State");
    dict.put(new Integer(0x00100010), "Patient's Name");
    dict.put(new Integer(0x00100020), "Patient ID");
    dict.put(new Integer(0x00100021), "Issuer of Patient ID");
    dict.put(new Integer(0x00100030), "Patient's Birth Date");
    dict.put(new Integer(0x00100032), "Patient's Birth Time");
    dict.put(new Integer(0x00100040), "Patient's Sex");
    dict.put(new Integer(0x00100050), "Patient's Insurance Plane Code");
    dict.put(new Integer(0x00100101), "Patient's Primary Language Code");
    dict.put(new Integer(0x00100102), "Patient's Primary Language Modifier");
    dict.put(new Integer(0x00101000), "Other Patient IDs");
    dict.put(new Integer(0x00101001), "Other Patient Names");
    dict.put(new Integer(0x00101005), "Patient's Birth Name");
    dict.put(new Integer(0x00101010), "Patient's Age");
    dict.put(new Integer(0x00101020), "Patient's Size");
    dict.put(new Integer(0x00101030), "Patient's Weight");
    dict.put(new Integer(0x00101040), "Patient's Address");
    dict.put(new Integer(0x00101060), "Patient's Mother's Birth Name");
    dict.put(new Integer(0x00101080), "Military Rank");
    dict.put(new Integer(0x00101081), "Branch of Service");
    dict.put(new Integer(0x00101090), "Medical Record Locator");
    dict.put(new Integer(0x00102000), "Medical Alerts");
    dict.put(new Integer(0x00102110), "Contrast Allergies");
    dict.put(new Integer(0x00102150), "Country of Residence");
    dict.put(new Integer(0x00102152), "Region of Residence");
    dict.put(new Integer(0x00102154), "Patient's Telephone Numbers");
    dict.put(new Integer(0x00102160), "Ethnic Group");
    dict.put(new Integer(0x00102180), "Occupation");
    dict.put(new Integer(0x001021a0), "Smoking Status");
    dict.put(new Integer(0x001021b0), "Additional Patient History");
    dict.put(new Integer(0x001021c0), "Pregnancy Status");
    dict.put(new Integer(0x001021d0), "Last Menstrual Date");
    dict.put(new Integer(0x001021f0), "Patient's Religious Preference");
    dict.put(new Integer(0x00104000), "Patient Comments");
    dict.put(new Integer(0x00120010), "Clinical Trial Sponsor Name");
    dict.put(new Integer(0x00120020), "Clinical Trial Protocol ID");
    dict.put(new Integer(0x00120021), "Clinical Trial Protocol Name");
    dict.put(new Integer(0x00120030), "Clinical Trial Site ID");
    dict.put(new Integer(0x00120031), "Clinical Trial Site Name");
    dict.put(new Integer(0x00120040), "Clinical Trial Subject ID");
    dict.put(new Integer(0x00120042), "Clinical Trial Subject Reading ID");
    dict.put(new Integer(0x00120050), "Clinical Trial Time Point ID");
    dict.put(new Integer(0x00120051), "Clinical Trial Time Point Description");
    dict.put(new Integer(0x00120060), "Clinical Trial Coordinating Center");
    dict.put(new Integer(0x00180010), "Contrast/Bolus Agent");
    dict.put(new Integer(0x00180012), "Contrast/Bolus Agent Sequence");
    dict.put(new Integer(0x00180014), "Contrast/Bolus Admin. Route Sequence");
    dict.put(new Integer(0x00180015), "Body Part Examined");
    dict.put(new Integer(0x00180020), "Scanning Sequence");
    dict.put(new Integer(0x00180021), "Sequence Variant");
    dict.put(new Integer(0x00180022), "Scan Options");
    dict.put(new Integer(0x00180023), "MR Acquisition Type");
    dict.put(new Integer(0x00180024), "Sequence Name");
    dict.put(new Integer(0x00180025), "Angio Flag");
    dict.put(new Integer(0x00180026),
      "Intervention Drug Information Sequence");
    dict.put(new Integer(0x00180027), "Intervention Drug Stop Time");
    dict.put(new Integer(0x00180028), "Intervention Drug Dose");
    dict.put(new Integer(0x00180029), "Intervention Drug Sequence");
    dict.put(new Integer(0x0018002a), "Additional Drug Sequence");
    dict.put(new Integer(0x00180031), "Radiopharmaceutical");
    dict.put(new Integer(0x00180034), "Intervention Drug Name");
    dict.put(new Integer(0x00180035), "Intervention Drug Start Time");
    dict.put(new Integer(0x00180036), "Intervention Sequence");
    dict.put(new Integer(0x00180038), "Intervention Status");
    dict.put(new Integer(0x0018003a), "Intervention Description");
    dict.put(new Integer(0x00180040), "Cine Rate");
    dict.put(new Integer(0x00180050), "Slice Thickness");
    dict.put(new Integer(0x00180060), "KVP");
    dict.put(new Integer(0x00180070), "Counts Accumulated");
    dict.put(new Integer(0x00180071), "Acquisition Termination Condition");
    dict.put(new Integer(0x00180072), "Effective Duration");
    dict.put(new Integer(0x00180073), "Acquisition Start Condition");
    dict.put(new Integer(0x00180074), "Acquisition Start Condition Data");
    dict.put(new Integer(0x00180075),
      "Acquisition Termination Condition Data");
    dict.put(new Integer(0x00180080), "Repetition Time");
    dict.put(new Integer(0x00180081), "Echo Time");
    dict.put(new Integer(0x00180082), "Inversion Time");
    dict.put(new Integer(0x00180083), "Number of Averages");
    dict.put(new Integer(0x00180084), "Imaging Frequency");
    dict.put(new Integer(0x00180085), "Imaged Nucleus");
    dict.put(new Integer(0x00180086), "Echo Number(s)");
    dict.put(new Integer(0x00180087), "Magnetic Field Strength");
    dict.put(new Integer(0x00180088), "Spacing Between Slices");
    dict.put(new Integer(0x00180089), "Number of Phase Encoding Steps");
    dict.put(new Integer(0x00180090), "Data Collection Diameter");
    dict.put(new Integer(0x00180091), "Echo Train Length");
    dict.put(new Integer(0x00180093), "Percent Sampling");
    dict.put(new Integer(0x00180094), "Percent Phase Field of View");
    dict.put(new Integer(0x00180095), "Pixel Bandwidth");
    dict.put(new Integer(0x00181000), "Device Serial Number");
    dict.put(new Integer(0x00181004), "Plate ID");
    dict.put(new Integer(0x00181010), "Secondary Capture Device ID");
    dict.put(new Integer(0x00181011), "Hardcopy Creation Device ID");
    dict.put(new Integer(0x00181012), "Date of Secondary Capture");
    dict.put(new Integer(0x00181014), "Time of Secondary Capture");
    dict.put(new Integer(0x00181016), "Secondary Capture Device Manufacturer");
    dict.put(new Integer(0x00181017), "Hardcopy Device Manufacturer");
    dict.put(new Integer(0x00181018), "Secondary Capture Device Model Name");
    dict.put(new Integer(0x00181019),
      "Secondary Capture Device Software Version");
    dict.put(new Integer(0x0018101a), "Hardcopy Device Software Version");
    dict.put(new Integer(0x0018101b), "Hardcopy Device Model Name");
    dict.put(new Integer(0x00181020), "Software Version(s)");
    dict.put(new Integer(0x00181022), "Video Image Format Acquired");
    dict.put(new Integer(0x00181023), "Digital Image Format Acquired");
    dict.put(new Integer(0x00181030), "Protocol Name");
    dict.put(new Integer(0x00181040), "Contrast/Bolus Route");
    dict.put(new Integer(0x00181041), "Contrast/Bolus Volume");
    dict.put(new Integer(0x00181042), "Contrast/Bolus Start Time");
    dict.put(new Integer(0x00181043), "Contrast/Bolus Stop Time");
    dict.put(new Integer(0x00181044), "Contrast/Bolus Total Dose");
    dict.put(new Integer(0x00181045), "Syringe Counts");
    dict.put(new Integer(0x00181046), "Contrast Flow Rate");
    dict.put(new Integer(0x00181047), "Contrast Flow Duration");
    dict.put(new Integer(0x00181048), "Contrast/Bolus Ingredient");
    dict.put(new Integer(0x00181049), "Contrast Ingredient Concentration");
    dict.put(new Integer(0x00181050), "Spatial Resolution");
    dict.put(new Integer(0x00181060), "Trigger Time");
    dict.put(new Integer(0x00181061), "Trigger Source or Type");
    dict.put(new Integer(0x00181062), "Nominal Interval");
    dict.put(new Integer(0x00181063), "Frame Time");
    dict.put(new Integer(0x00181064), "Framing Type");
    dict.put(new Integer(0x00181065), "Frame Time Vector");
    dict.put(new Integer(0x00181066), "Frame Delay");
    dict.put(new Integer(0x00181067), "Image Trigger Delay");
    dict.put(new Integer(0x00181068), "Multiplex Group Time Offset");
    dict.put(new Integer(0x00181069), "Trigger Time Offset");
    dict.put(new Integer(0x0018106a), "Synchronization Trigger");
    dict.put(new Integer(0x0018106c), "Synchronization Channel");
    dict.put(new Integer(0x0018106e), "Trigger Sample Position");
    dict.put(new Integer(0x00181070), "Radiopharmaceutical Route");
    dict.put(new Integer(0x00181071), "Radiopharmaceutical Volume");
    dict.put(new Integer(0x00181072), "Radiopharmaceutical Start Time");
    dict.put(new Integer(0x00181073), "Radiopharmaceutical Stop Time");
    dict.put(new Integer(0x00181074), "Radionuclide Total Dose");
    dict.put(new Integer(0x00181075), "Radionuclide Half Life");
    dict.put(new Integer(0x00181076), "Radionuclide Positron Fraction");
    dict.put(new Integer(0x00181077), "Radiopharmaceutical Specific Activity");
    dict.put(new Integer(0x00181080), "Beat Rejection Flag");
    dict.put(new Integer(0x00181081), "Low R-R Value");
    dict.put(new Integer(0x00181082), "High R-R Value");
    dict.put(new Integer(0x00181083), "Intervals Acquired");
    dict.put(new Integer(0x00181084), "Intervals Rejected");
    dict.put(new Integer(0x00181085), "PVC Rejection");
    dict.put(new Integer(0x00181086), "Skip Beats");
    dict.put(new Integer(0x00181088), "Heart Rate");
    dict.put(new Integer(0x00181090), "Cardiac Number of Images");
    dict.put(new Integer(0x00181094), "Trigger Window");
    dict.put(new Integer(0x00181100), "Reconstruction Diameter");
    dict.put(new Integer(0x00181110), "Distance Source to Detector");
    dict.put(new Integer(0x00181111), "Distance Source to Patient");
    dict.put(new Integer(0x00181114), "Estimated Radiographic Mag. Factor");
    dict.put(new Integer(0x00181120), "Gantry/Detector Tilt");
    dict.put(new Integer(0x00181121), "Gantry/Detector Skew");
    dict.put(new Integer(0x00181130), "Table Height");
    dict.put(new Integer(0x00181131), "Table Traverse");
    dict.put(new Integer(0x00181134), "Table Motion");
    dict.put(new Integer(0x00181135), "Table Vertical Increment");
    dict.put(new Integer(0x00181136), "Table Lateral Increment");
    dict.put(new Integer(0x00181137), "Table Longitudinal Increment");
    dict.put(new Integer(0x00181138), "Table Angle");
    dict.put(new Integer(0x0018113a), "Table Type");
    dict.put(new Integer(0x00181140), "Rotation Direction");
    dict.put(new Integer(0x00181141), "Angular Position");
    dict.put(new Integer(0x00181142), "Radial Position");
    dict.put(new Integer(0x00181143), "Scan Arc");
    dict.put(new Integer(0x00181144), "Angular Step");
    dict.put(new Integer(0x00181145), "Center of Rotation Offset");
    dict.put(new Integer(0x00181147), "Field of View Shape");
    dict.put(new Integer(0x00181149), "Field of View Dimension(s)");
    dict.put(new Integer(0x00181150), "Exposure Time");
    dict.put(new Integer(0x00181151), "X-ray Tube Current");
    dict.put(new Integer(0x00181152), "Exposure");
    dict.put(new Integer(0x00181153), "Exposure in uAs");
    dict.put(new Integer(0x00181154), "Average Pulse Width");
    dict.put(new Integer(0x00181155), "Radiation Setting");
    dict.put(new Integer(0x00181156), "Rectification Type");
    dict.put(new Integer(0x0018115a), "Radiation Mode");
    dict.put(new Integer(0x0018115e), "Image Area Dose Product");
    dict.put(new Integer(0x00181160), "Filter Type");
    dict.put(new Integer(0x00181161), "Type of Filters");
    dict.put(new Integer(0x00181162), "Intensifier Size");
    dict.put(new Integer(0x00181164), "Imager Pixel Spacing");
    dict.put(new Integer(0x00181166), "Grid");
    dict.put(new Integer(0x00181170), "Generator Power");
    dict.put(new Integer(0x00181180), "Collimator/Grid Name");
    dict.put(new Integer(0x00181181), "Collimator Type");
    dict.put(new Integer(0x00181182), "Focal Distance");
    dict.put(new Integer(0x00181183), "X Focus Center");
    dict.put(new Integer(0x00181184), "Y Focus Center");
    dict.put(new Integer(0x00181190), "Focal Spot(s)");
    dict.put(new Integer(0x00181191), "Anode Target Material");
    dict.put(new Integer(0x001811a0), "Body Part Thickness");
    dict.put(new Integer(0x001811a2), "Compression Force");
    dict.put(new Integer(0x00181200), "Date of Last Calibration");
    dict.put(new Integer(0x00181201), "Time of Last Calibration");
    dict.put(new Integer(0x00181210), "Convolution Kernel");
    dict.put(new Integer(0x00181242), "Actual Frame Duration");
    dict.put(new Integer(0x00181243), "Count Rate");
    dict.put(new Integer(0x00181244), "Preferred Playback Sequencing");
    dict.put(new Integer(0x00181250), "Receive Coil Name");
    dict.put(new Integer(0x00181251), "Transmit Coil Name");
    dict.put(new Integer(0x00181260), "Plate Type");
    dict.put(new Integer(0x00181261), "Phosphor Type");
    dict.put(new Integer(0x00181300), "Scan Velocity");
    dict.put(new Integer(0x00181301), "Whole Body Technique");
    dict.put(new Integer(0x00181302), "Scan Length");
    dict.put(new Integer(0x00181310), "Acquisition Matrix");
    dict.put(new Integer(0x00181312), "In-plane Phase Encoding Direction");
    dict.put(new Integer(0x00181314), "Flip Angle");
    dict.put(new Integer(0x00181315), "Variable Flip Angle Flag");
    dict.put(new Integer(0x00181316), "SAR");
    dict.put(new Integer(0x00181318), "dB/dt");
    dict.put(new Integer(0x00181400), "Acquisition Device Processing Descr.");
    dict.put(new Integer(0x00181401), "Acquisition Device Processing Code");
    dict.put(new Integer(0x00181402), "Cassette Orientation");
    dict.put(new Integer(0x00181403), "Cassette Size");
    dict.put(new Integer(0x00181404), "Exposures on Plate");
    dict.put(new Integer(0x00181405), "Relative X-ray Exposure");
    dict.put(new Integer(0x00181450), "Column Angulation");
    dict.put(new Integer(0x00181460), "Tomo Layer Height");
    dict.put(new Integer(0x00181470), "Tomo Angle");
    dict.put(new Integer(0x00181480), "Tomo Time");
    dict.put(new Integer(0x00181490), "Tomo Type");
    dict.put(new Integer(0x00181491), "Tomo Class");
    dict.put(new Integer(0x00181495), "Number of Tomosynthesis Source Images");
    dict.put(new Integer(0x00181500), "Positioner Motion");
    dict.put(new Integer(0x00181508), "Positioner Type");
    dict.put(new Integer(0x00181510), "Positioner Primary Angle");
    dict.put(new Integer(0x00181511), "Positioner Secondary Angle");
    dict.put(new Integer(0x00181520), "Positioner Primary Angle Increment");
    dict.put(new Integer(0x00181521), "Positioner Secondary Angle Increment");
    dict.put(new Integer(0x00181530), "Detector Primary Angle");
    dict.put(new Integer(0x00181531), "Detector Secondary Angle");
    dict.put(new Integer(0x00181600), "Shutter Shape");
    dict.put(new Integer(0x00181602), "Shutter Left Vertical Edge");
    dict.put(new Integer(0x00181604), "Shutter Right Vertical Edge");
    dict.put(new Integer(0x00181606), "Shutter Upper Horizontal Edge");
    dict.put(new Integer(0x00181608), "Shutter Lower Horizontal Edge");
    dict.put(new Integer(0x00181610), "Center of Circular Shutter");
    dict.put(new Integer(0x00181612), "Radius of Circular Shutter");
    dict.put(new Integer(0x00181620), "Vertices of the Polygonal Shutter");
    dict.put(new Integer(0x00181622), "Shutter Presentation Value");
    dict.put(new Integer(0x00181623), "Shutter Overlay Group");
    dict.put(new Integer(0x00181700), "Collimator Shape");
    dict.put(new Integer(0x00181702), "Collimator Left Vertical Edge");
    dict.put(new Integer(0x00181704), "Collimator Right Vertical Edge");
    dict.put(new Integer(0x00181706), "Collimator Upper Horizontal Edge");
    dict.put(new Integer(0x00181708), "Collimator Lower Horizontal Edge");
    dict.put(new Integer(0x00181710), "Center of Circular Collimator");
    dict.put(new Integer(0x00181712), "Radius of Circular Collimator");
    dict.put(new Integer(0x00181720), "Vertices of the polygonal Collimator");
    dict.put(new Integer(0x00181800), "Acquisition Time Synchronized");
    dict.put(new Integer(0x00181801), "Time Source");
    dict.put(new Integer(0x00181802), "Time Distribution Protocol");
    dict.put(new Integer(0x00181803), "NTP Source Address");
    dict.put(new Integer(0x00182001), "Page Number Vector");
    dict.put(new Integer(0x00182002), "Frame Label Vector");
    dict.put(new Integer(0x00182003), "Frame Primary Angle Vector");
    dict.put(new Integer(0x00182004), "Frame Secondary Angle Vector");
    dict.put(new Integer(0x00182005), "Slice Location Vector");
    dict.put(new Integer(0x00182006), "Display Window Label Vector");
    dict.put(new Integer(0x00182010), "Nominal Scanned Pixel Spacing");
    dict.put(new Integer(0x00182020), "Digitizing Device Transport Direction");
    dict.put(new Integer(0x00182030), "Rotation of Scanned Film");
    dict.put(new Integer(0x00183100), "IVUS Acquisition");
    dict.put(new Integer(0x00183101), "IVUS Pullback Rate");
    dict.put(new Integer(0x00183102), "IVUS Gated Rate");
    dict.put(new Integer(0x00183103), "IVUS Pullback Start Frame Number");
    dict.put(new Integer(0x00183104), "IVUS Pullback Stop Frame Number");
    dict.put(new Integer(0x00183105), "Lesion Number");
    dict.put(new Integer(0x00185000), "Output Power");
    dict.put(new Integer(0x00185010), "Transducer Data");
    dict.put(new Integer(0x00185012), "Focus Depth");
    dict.put(new Integer(0x00185020), "Processing Function");
    dict.put(new Integer(0x00185021), "Postprocessing Fuction");
    dict.put(new Integer(0x00185022), "Mechanical Index");
    dict.put(new Integer(0x00185024), "Bone Thermal Index");
    dict.put(new Integer(0x00185026), "Cranial Thermal Index");
    dict.put(new Integer(0x00185027), "Soft Tissue Thermal Index");
    dict.put(new Integer(0x00185028), "Soft Tissue-focus Thermal Index");
    dict.put(new Integer(0x00185029), "Soft Tissue-surface Thermal Index");
    dict.put(new Integer(0x00185050), "Depth of scan field");
    dict.put(new Integer(0x00185100), "Patient Position");
    dict.put(new Integer(0x00185101), "View Position");
    dict.put(new Integer(0x00185104), "Projection Eponymous Name Code");
    dict.put(new Integer(0x00186000), "Sensitivity");
    dict.put(new Integer(0x00186011), "Sequence of Ultrasound Regions");
    dict.put(new Integer(0x00186012), "Region Spatial Format");
    dict.put(new Integer(0x00186014), "Region Data Type");
    dict.put(new Integer(0x00186016), "Region Flags");
    dict.put(new Integer(0x00186018), "Region Location Min X0");
    dict.put(new Integer(0x0018601a), "Region Location Min Y0");
    dict.put(new Integer(0x0018601c), "Region Location Max X1");
    dict.put(new Integer(0x0018601e), "Region Location Max Y1");
    dict.put(new Integer(0x00186020), "Reference Pixel X0");
    dict.put(new Integer(0x00186022), "Reference Pixel Y0");
    dict.put(new Integer(0x00186024), "Physical Units X Direction");
    dict.put(new Integer(0x00186026), "Physical Units Y Direction");
    dict.put(new Integer(0x00186028), "Reference Pixel Physical Value X");
    dict.put(new Integer(0x0018602a), "Reference Pixel Physical Value Y");
    dict.put(new Integer(0x0018602c), "Physical Delta X");
    dict.put(new Integer(0x0018602e), "Physical Delta Y");
    dict.put(new Integer(0x00186030), "Transducer Frequency");
    dict.put(new Integer(0x00186031), "Transducer Type");
    dict.put(new Integer(0x00186032), "Pulse Repetition Frequency");
    dict.put(new Integer(0x00186034), "Doppler Correction Angle");
    dict.put(new Integer(0x00186036), "Steering Angle");
    dict.put(new Integer(0x00186039), "Doppler Sample Volume X Position");
    dict.put(new Integer(0x0018603b), "Doppler Sample Volume Y Position");
    dict.put(new Integer(0x0018603d), "TM-Line Position X0");
    dict.put(new Integer(0x0018603f), "TM-Line Position Y0");
    dict.put(new Integer(0x00186041), "TM-Line Position X1");
    dict.put(new Integer(0x00186043), "TM-Line Position Y1");
    dict.put(new Integer(0x00186044), "Pixel Component Organization");
    dict.put(new Integer(0x00186046), "Pixel Component Mask");
    dict.put(new Integer(0x00186048), "Pixel Component Range Start");
    dict.put(new Integer(0x0018604a), "Pixel Component Range Stop");
    dict.put(new Integer(0x0018604c), "Pixel Component Physical Units");
    dict.put(new Integer(0x0018604e), "Pixel Component Data Type");
    dict.put(new Integer(0x00186050), "Number of Table Break Points");
    dict.put(new Integer(0x00186052), "Table of X Break Points");
    dict.put(new Integer(0x00186054), "Table of Y Break Points");
    dict.put(new Integer(0x00186056), "Number of Table Entries");
    dict.put(new Integer(0x00186058), "Table of Pixel Values");
    dict.put(new Integer(0x0018605a), "Table of Parameter Values");
    dict.put(new Integer(0x00186060), "R Wave Time Vector");
    dict.put(new Integer(0x00187000), "Detector Conditions Nominal Flag");
    dict.put(new Integer(0x00187001), "Detector Temperature");
    dict.put(new Integer(0x00187004), "Detector Type");
    dict.put(new Integer(0x00187005), "Detector Configuration");
    dict.put(new Integer(0x00187006), "Detector Description");
    dict.put(new Integer(0x00187008), "Detector Mode");
    dict.put(new Integer(0x0018700a), "Detector ID");
    dict.put(new Integer(0x0018700c), "Date of Last Detector Calibration");
    dict.put(new Integer(0x0018700e), "Time of Last Detector Calibration");
    dict.put(new Integer(0x00187012), "Detector Time Since Last Exposure");
    dict.put(new Integer(0x00187014), "Detector Active Time");
    dict.put(new Integer(0x00187016), "Detector Activation Offset");
    dict.put(new Integer(0x0018701a), "Detector Binning");
    dict.put(new Integer(0x00187020), "Detector Element Physical Size");
    dict.put(new Integer(0x00187022), "Detector Element Spacing");
    dict.put(new Integer(0x00187024), "Detector Active Shape");
    dict.put(new Integer(0x00187026), "Detector Active Dimension(s)");
    dict.put(new Integer(0x00187028), "Detector Active Origin");
    dict.put(new Integer(0x0018702a), "Detector Manufacturer Name");
    dict.put(new Integer(0x0018702b), "Detector Model Name");
    dict.put(new Integer(0x00187030), "Field of View Origin");
    dict.put(new Integer(0x00187032), "Field of View Rotation");
    dict.put(new Integer(0x00187034), "Field of View Horizontal Flip");
    dict.put(new Integer(0x00187040), "Grid Absorbing Material");
    dict.put(new Integer(0x00187041), "Grid Spacing Material");
    dict.put(new Integer(0x00187042), "Grid Thickness");
    dict.put(new Integer(0x00187044), "Grid Pitch");
    dict.put(new Integer(0x00187046), "Grid Aspect Ratio");
    dict.put(new Integer(0x00187048), "Grid Period");
    dict.put(new Integer(0x0018704c), "Grid Focal Distance");
    dict.put(new Integer(0x00187050), "Filter Material");
    dict.put(new Integer(0x00187052), "Filter Thickness Min");
    dict.put(new Integer(0x00187054), "Filter Thickness Max");
    dict.put(new Integer(0x00187060), "Exposure Control Mode");
    dict.put(new Integer(0x0020000d), "Study Instance UID");
    dict.put(new Integer(0x0020000e), "Series Instance UID");
    dict.put(new Integer(0x00200011), "Series Number");
    dict.put(new Integer(0x00200012), "Acquisition Number");
    dict.put(new Integer(0x00200013), "Instance Number");
    dict.put(new Integer(0x00200020), "Patient Orientation");
    dict.put(new Integer(0x00200030), "Image Position");
    dict.put(new Integer(0x00200032), "Image Position (Patient)");
    dict.put(new Integer(0x00200037), "Image Orientation (Patient)");
    dict.put(new Integer(0x00200050), "Location");
    dict.put(new Integer(0x00200052), "Frame of Reference UID");
    dict.put(new Integer(0x00200070), "Image Geometry Type");
    dict.put(new Integer(0x00201001), "Acquisitions in Series");
    dict.put(new Integer(0x00201020), "Reference");
    dict.put(new Integer(0x00201041), "Slice Location");
    // skipped a bunch of stuff here - not used
    dict.put(new Integer(0x00280002), "Samples per pixel");
    dict.put(new Integer(0x00280003), "Samples per pixel used");
    dict.put(new Integer(0x00280004), "Photometric Interpretation");
    dict.put(new Integer(0x00280006), "Planar Configuration");
    dict.put(new Integer(0x00280008), "Number of frames");
    dict.put(new Integer(0x00280009), "Frame Increment Pointer");
    dict.put(new Integer(0x0028000a), "Frame Dimension Pointer");
    dict.put(new Integer(0x00280010), "Rows");
    dict.put(new Integer(0x00280011), "Columns");
    dict.put(new Integer(0x00280012), "Planes");
    dict.put(new Integer(0x00280014), "Ultrasound Color Data Present");
    dict.put(new Integer(0x00280030), "Pixel Spacing");
    dict.put(new Integer(0x00280031), "Zoom Factor");
    dict.put(new Integer(0x00280032), "Zoom Center");
    dict.put(new Integer(0x00280034), "Pixel Aspect Ratio");
    dict.put(new Integer(0x00280051), "Corrected Image");
    dict.put(new Integer(0x00280100), "Bits Allocated");
    dict.put(new Integer(0x00280101), "Bits Stored");
    dict.put(new Integer(0x00280102), "High Bit");
    dict.put(new Integer(0x00280103), "Pixel Representation");
    dict.put(new Integer(0x00280106), "Smallest Image Pixel Value");
    dict.put(new Integer(0x00280107), "Largest Image Pixel Value");
    dict.put(new Integer(0x00280108), "Smallest Pixel Value in Series");
    dict.put(new Integer(0x00280109), "Largest Pixel Value in Series");
    dict.put(new Integer(0x00280110), "Smallest Image Pixel Value in Plane");
    dict.put(new Integer(0x00280111), "Largest Image Pixel Value in Plane");
    dict.put(new Integer(0x00280120), "Pixel Padding Value");
    dict.put(new Integer(0x00280300), "Quality Control Image");
    dict.put(new Integer(0x00280301), "Burned in Annotation");
    dict.put(new Integer(0x00281040), "Pixel Intensity Relationship");
    dict.put(new Integer(0x00281041), "Pixel Intensity Relationship Sign");
    dict.put(new Integer(0x00281050), "Window Center");
    dict.put(new Integer(0x00281051), "Window Width");
    dict.put(new Integer(0x00281052), "Rescale Intercept");
    dict.put(new Integer(0x00281053), "Rescale Slope");
    dict.put(new Integer(0x00281054), "Rescale Type");
    dict.put(new Integer(0x00281055), "Window Center and Width Explanation");
    dict.put(new Integer(0x00281090), "Recommended Viewing Mode");
    dict.put(new Integer(0x00281101), "Red Palette Color LUT Descriptor");
    dict.put(new Integer(0x00281102), "Green Palette Color LUT Descriptor");
    dict.put(new Integer(0x00281103), "Blue Palette Color LUT Descriptor");
    dict.put(new Integer(0x00281199), "Palette Color LUT UID");
    dict.put(new Integer(0x00281201), "Red Palette Color LUT Data");
    dict.put(new Integer(0x00281202), "Green Palette Color LUT Data");
    dict.put(new Integer(0x00281203), "Blue Palette Color LUT Data");
    dict.put(new Integer(0x00281221), "Segmented Red Palette Color LUT Data");
    dict.put(new Integer(0x00281222),
      "Segmented Green Palette Color LUT Data");
    dict.put(new Integer(0x00281223), "Segmented Blue Palette Color LUT Data");
    dict.put(new Integer(0x00281300), "Implant Present");
    dict.put(new Integer(0x00281350), "Partial View");
    dict.put(new Integer(0x00281351), "Partial View Description");
    dict.put(new Integer(0x00282110), "Lossy Image Compression");
    dict.put(new Integer(0x00282112), "Lossy Image Compression Ratio");
    dict.put(new Integer(0x00282114), "Lossy Image Compression Method");
    dict.put(new Integer(0x00283000), "Modality LUT Sequence");
    dict.put(new Integer(0x00283002), "LUT Descriptor");
    dict.put(new Integer(0x00283003), "LUT Explanation");
    dict.put(new Integer(0x00283004), "Modality LUT Type");
    dict.put(new Integer(0x00283006), "LUT Data");
    dict.put(new Integer(0x00283010), "VOI LUT Sequence");
    dict.put(new Integer(0x00283110), "Softcopy VOI LUT Sequence");
    dict.put(new Integer(0x00285000), "Bi-Plane Acquisition Sequence");
    dict.put(new Integer(0x00286010), "Representative Frame Number");
    dict.put(new Integer(0x00286020), "Frame Numbers of Interest (FOI)");
    dict.put(new Integer(0x00286022), "Frame(s) of Interest Description");
    dict.put(new Integer(0x00286023), "Frame of Interest Type");
    dict.put(new Integer(0x00286040), "R Wave Pointer");
    dict.put(new Integer(0x00286100), "Mask Subtraction Sequence");
    dict.put(new Integer(0x00286101), "Mask Operation");
    dict.put(new Integer(0x00286102), "Applicable Frame Range");
    dict.put(new Integer(0x00286110), "Mask Frame Numbers");
    dict.put(new Integer(0x00286112), "Contrast Frame Averaging");
    dict.put(new Integer(0x00286114), "Mask Sub-pixel Shift");
    dict.put(new Integer(0x00286120), "TID Offset");
    dict.put(new Integer(0x00286190), "Mask Operation Explanation");
    dict.put(new Integer(0x00289001), "Data Point Rows");
    dict.put(new Integer(0x00289002), "Data Point Columns");
    dict.put(new Integer(0x00289003), "Signal Domain Columns");
    dict.put(new Integer(0x00289108), "Data Representation");
    dict.put(new Integer(0x00289110), "Pixel Measures Sequence");
    dict.put(new Integer(0x00289132), "Frame VOI LUT Sequence");
    dict.put(new Integer(0x00289145), "Pixel Value Transformation Sequence");
    dict.put(new Integer(0x00289235), "Signal Domain Rows");
    // skipping some more stuff
    dict.put(new Integer(0x00540011), "Number of Energy Windows");
    dict.put(new Integer(0x00540021), "Number of Detectors");
    dict.put(new Integer(0x00540051), "Number of Rotations");
    dict.put(new Integer(0x00540080), "Slice Vector");
    dict.put(new Integer(0x00540081), "Number of Slices");
    dict.put(new Integer(0x00540202), "Type of Detector Motion");
    dict.put(new Integer(0x00540400), "Image ID");
    dict.put(new Integer(0x20100100), "Border Density");

    return dict;
  }

}
