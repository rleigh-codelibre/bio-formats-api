//
// IPLabReader.java
//

/*
OME Bio-Formats package for reading and converting biological file formats.
Copyright (C) 2005-@year@ UW-Madison LOCI and Glencoe Software, Inc.

This program is free software; you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation; either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program; if not, write to the Free Software
Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
*/

package loci.formats.in;

import java.io.IOException;
import loci.formats.*;
import loci.formats.meta.FilterMetadata;
import loci.formats.meta.MetadataStore;

/**
 * IPLabReader is the file format reader for IPLab (.IPL) files.
 *
 * <dl><dt><b>Source code:</b></dt>
 * <dd><a href="https://skyking.microscopy.wisc.edu/trac/java/browser/trunk/loci/formats/in/IPLabReader.java">Trac</a>,
 * <a href="https://skyking.microscopy.wisc.edu/svn/java/trunk/loci/formats/in/IPLabReader.java">SVN</a></dd></dl>
 *
 * @author Melissa Linkert linkert at wisc.edu
 * @author Curtis Rueden ctrueden at wisc.edu
 */
public class IPLabReader extends FormatReader {

  // -- Fields --

  /** Bytes per pixel. */
  private int bps;

  /** Total number of pixel bytes. */
  private int dataSize;

  // -- Constructor --

  /** Constructs a new IPLab reader. */
  public IPLabReader() {
    super("IPLab", "ipl");
    blockCheckLen = 12;
    suffixNecessary = false; // allow extensionless IPLab files
  }

  // -- IFormatReader API methods --

  /* @see loci.formats.IFormatReader#isThisType(byte[]) */
  public boolean isThisType(byte[] block) {
    if (block.length < blockCheckLen) return false; // block length too short
    String s = new String(block, 0, 4);
    boolean big = s.equals("iiii");
    boolean little = s.equals("mmmm");
    if (!big && !little) return false;
    int size = DataTools.bytesToInt(block, 4, 4, little);
    if (size != 4) return false; // first block size should be 4
    int version = DataTools.bytesToInt(block, 8, 4, little);
    return version >= 0x100e;
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

    int numPixels = core.sizeX[0] * core.sizeY[0] * core.sizeC[0] * bps;
    in.seek(numPixels * (no / core.sizeC[0]) + 44);

    for (int c=0; c<core.sizeC[0]; c++) {
      in.skipBytes(y * core.sizeX[0] * bps);

      for (int row=0; row<h; row++) {
        in.skipBytes(x * bps);
        in.read(buf, c * h * w * bps + row * w * bps, w * bps);
        in.skipBytes(bps * (core.sizeX[0] - w - x));
      }
      in.skipBytes((core.sizeY[0] - h - y) * core.sizeX[0] * bps);
    }

    return buf;
  }

  // -- IFormatHandler API methods --

  /* @see loci.formats.IFormatHandler#close() */
  public void close() throws IOException {
    super.close();
    bps = dataSize = 0;
  }

  // -- Internal FormatReader API methods --

  /* @see loci.formats.FormatReader#initFile(String) */
  protected void initFile(String id) throws FormatException, IOException {
    if (debug) debug("IPLabReader.initFile(" + id + ")");
    super.initFile(id);
    in = new RandomAccessStream(id);

    status("Populating metadata");

    core.littleEndian[0] = in.readString(4).equals("iiii");

    in.order(core.littleEndian[0]);

    in.skipBytes(12);

    // read axis sizes from header

    dataSize = in.readInt() - 28;
    core.sizeX[0] = in.readInt();
    core.sizeY[0] = in.readInt();
    core.sizeC[0] = in.readInt();
    core.sizeZ[0] = in.readInt();
    core.sizeT[0] = in.readInt();
    int filePixelType = in.readInt();

    core.imageCount[0] = core.sizeZ[0] * core.sizeT[0];

    addMeta("Width", new Long(core.sizeX[0]));
    addMeta("Height", new Long(core.sizeY[0]));
    addMeta("Channels", new Long(core.sizeC[0]));
    addMeta("ZDepth", new Long(core.sizeZ[0]));
    addMeta("TDepth", new Long(core.sizeT[0]));

    String ptype;
    switch (filePixelType) {
      case 0:
        ptype = "8 bit unsigned";
        core.pixelType[0] = FormatTools.UINT8;
        break;
      case 1:
        ptype = "16 bit signed short";
        core.pixelType[0] = FormatTools.INT16;
        break;
      case 2:
        ptype = "16 bit unsigned short";
        core.pixelType[0] = FormatTools.UINT16;
        break;
      case 3:
        ptype = "32 bit signed long";
        core.pixelType[0] = FormatTools.INT32;
        break;
      case 4:
        ptype = "32 bit single-precision float";
        core.pixelType[0] = FormatTools.FLOAT;
        break;
      case 5:
        ptype = "Color24";
        core.pixelType[0] = FormatTools.UINT32;
        break;
      case 6:
        ptype = "Color48";
        core.pixelType[0] = FormatTools.UINT16;
        break;
      case 10:
        ptype = "64 bit double-precision float";
        core.pixelType[0] = FormatTools.DOUBLE;
        break;
      default:
        ptype = "reserved"; // for values 7-9
    }

    bps = FormatTools.getBytesPerPixel(core.pixelType[0]);

    addMeta("PixelType", ptype);
    in.skipBytes(dataSize);

    core.currentOrder[0] = "XY";
    if (core.sizeC[0] > 1) core.currentOrder[0] += "CZT";
    else core.currentOrder[0] += "ZTC";

    core.rgb[0] = core.sizeC[0] > 1;
    core.interleaved[0] = false;
    core.indexed[0] = false;
    core.falseColor[0] = false;
    core.metadataComplete[0] = true;

    // The metadata store we're working with.
    MetadataStore store =
      new FilterMetadata(getMetadataStore(), isMetadataFiltered());
    store.setImageName("", 0);
    store.setImageCreationDate(
      DataTools.convertDate(System.currentTimeMillis(), DataTools.UNIX), 0);
    MetadataTools.populatePixels(store, this);

    status("Reading tags");

    String tag = in.readString(4);
    while (!tag.equals("fini") && in.getFilePointer() < in.length() - 4) {
      if (tag.equals("clut")) {
        // read in Color Lookup Table
        int size = in.readInt();
        if (size == 8) {
          // indexed lookup table
          in.skipBytes(4);
          int type = in.readInt();

          String[] types = new String[] {
            "monochrome", "reverse monochrome", "BGR", "classify", "rainbow",
            "red", "green", "blue", "cyan", "magenta", "yellow",
            "saturated pixels"
          };
          String clutType = (type >= 0 && type < types.length) ? types[type] :
            "unknown";
          addMeta("LUT type", clutType);
        }
        else {
          // explicitly defined lookup table
          // length is 772
          in.skipBytes(772);
        }
      }
      else if (tag.equals("norm")) {
        // read in normalization information

        int size = in.readInt();
        // error checking

        if (size != (44 * core.sizeC[0])) {
          throw new FormatException("Bad normalization settings");
        }

        String[] types = new String[] {
          "user", "plane", "sequence", "saturated plane",
          "saturated sequence", "ROI"
        };

        for (int i=0; i<core.sizeC[0]; i++) {
          int source = in.readInt();

          String sourceType = (source >= 0 && source < types.length) ?
            types[source] : "user";
          addMeta("NormalizationSource" + i, sourceType);

          double min = in.readDouble();
          double max = in.readDouble();
          double gamma = in.readDouble();
          double black = in.readDouble();
          double white = in.readDouble();

          addMeta("NormalizationMin" + i, new Double(min));
          addMeta("NormalizationMax" + i, new Double(max));
          addMeta("NormalizationGamma" + i, new Double(gamma));
          addMeta("NormalizationBlack" + i, new Double(black));
          addMeta("NormalizationWhite" + i, new Double(white));

          // CTR CHECK
//          store.setChannelGlobalMinMax(i, new Double(min),
//            new Double(max), null);

          // CTR CHECK
//          store.setDisplayChannel(new Integer(core.sizeC[0]), new Double(black),
//            new Double(white), new Float(gamma), null);
        }
      }
      else if (tag.equals("head")) {
        // read in header labels

        int size = in.readInt();
        for (int i=0; i<size / 22; i++) {
          int num = in.readShort();
          addMeta("Header" + num, in.readString(20));
        }
      }
      else if (tag.equals("mmrc")) {
        in.skipBytes(in.readInt());
      }
      else if (tag.equals("roi ")) {
        // read in ROI information

        in.skipBytes(8);
        int roiLeft = in.readInt();
        int roiTop = in.readInt();
        int roiRight = in.readInt();
        int roiBottom = in.readInt();
        int numRoiPts = in.readInt();

        Integer x0 = new Integer(roiLeft);
        Integer x1 = new Integer(roiRight);
        Integer y0 = new Integer(roiBottom);
        Integer y1 = new Integer(roiTop);
        // TODO
        //store.setDisplayROIX0(x0, 0, 0);
        //store.setDisplayROIY0(y0, 0, 0);
        //store.setDisplayROIX1(x1, 0, 0);
        //store.setDisplayROIY1(y1, 0, 0);

        in.skipBytes(8 * numRoiPts);
      }
      else if (tag.equals("mask")) {
        // read in Segmentation Mask
        in.skipBytes(in.readInt());
      }
      else if (tag.equals("unit")) {
        // read in units
        in.skipBytes(4);

        for (int i=0; i<4; i++) {
          int xResStyle = in.readInt();
          float unitsPerPixel = in.readFloat();
          int xUnitName = in.readInt();

          addMeta("ResolutionStyle" + i, new Long(xResStyle));
          addMeta("UnitsPerPixel" + i, new Float(unitsPerPixel));

          switch (xUnitName) {
            case 2: // mm
              unitsPerPixel *= 1000;
              break;
            case 3: // cm
              unitsPerPixel *= 10000;
              break;
            case 4: // m
              unitsPerPixel *= 1000000;
              break;
            case 5: // inch
              unitsPerPixel *= 3937;
              break;
            case 6: //ft
              unitsPerPixel *= 47244;
              break;
          }

          if (i == 0) {
            Float pixelSize = new Float(unitsPerPixel);
            store.setDimensionsPhysicalSizeX(pixelSize, 0, 0);
            store.setDimensionsPhysicalSizeY(pixelSize, 0, 0);
          }

          addMeta("UnitName" + i, new Long(xUnitName));
        }
      }
      else if (tag.equals("view")) {
        // read in view
        in.skipBytes(4);
      }
      else if (tag.equals("plot")) {
        // read in plot
        // skipping this field for the moment
        in.skipBytes(2512);
      }
      else if (tag.equals("notes")) {
        // read in notes (image info)
        in.skipBytes(4);
        String descriptor = in.readString(64);
        String notes = in.readString(512);
        addMeta("Descriptor", descriptor);
        addMeta("Notes", notes);

        store.setImageDescription(notes, 0);
      }

      if (in.getFilePointer() + 4 <= in.length()) {
        tag = in.readString(4);
      }
      else {
        tag = "fini";
      }
      if (in.getFilePointer() >= in.length() && !tag.equals("fini")) {
        tag = "fini";
      }
    }
  }

}
