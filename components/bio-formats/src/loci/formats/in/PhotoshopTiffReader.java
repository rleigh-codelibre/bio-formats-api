//
// PhotoshopTiffReader.java
//

/*
OME Bio-Formats package for reading and converting biological file formats.
Copyright (C) 2005-@year@ UW-Madison LOCI and Glencoe Software, Inc.

This program is free software; you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation; either version 2 of the License, or
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
import java.util.StringTokenizer;
import java.util.Vector;

import loci.common.DataTools;
import loci.common.RandomAccessInputStream;
import loci.formats.CoreMetadata;
import loci.formats.FormatException;
import loci.formats.FormatTools;
import loci.formats.MetadataTools;
import loci.formats.codec.ByteVector;
import loci.formats.codec.Codec;
import loci.formats.codec.CodecOptions;
import loci.formats.codec.PackbitsCodec;
import loci.formats.codec.ZlibCodec;
import loci.formats.meta.FilterMetadata;
import loci.formats.meta.MetadataStore;
import loci.formats.tiff.IFD;
import loci.formats.tiff.IFDList;
import loci.formats.tiff.TiffParser;

/**
 * PhotoshopTiffReader is the file format reader for
 * Adobe Photoshop TIFF files.
 *
 * <dl><dt><b>Source code:</b></dt>
 * <dd><a href="https://skyking.microscopy.wisc.edu/trac/java/browser/trunk/components/bio-formats/src/loci/formats/in/PhotoshopTiffReader.java">Trac</a>,
 * <a href="https://skyking.microscopy.wisc.edu/svn/java/trunk/components/bio-formats/src/loci/formats/in/PhotoshopTiffReader.java">SVN</a></dd></dl>
 *
 * @author Melissa Linkert linkert at wisc.edu
 */
public class PhotoshopTiffReader extends BaseTiffReader {

  // -- Constants --

  public static final int IMAGE_SOURCE_DATA = 37724;

  public static final int PACKBITS = 1;
  public static final int ZIP = 3;

  // -- Fields --

  private RandomAccessInputStream tag;
  private long[] layerOffset;
  private int[] compression;
  private int[][] channelOrder;
  private String[] layerNames;

  // -- Constructor --

  /** Constructs a new Photoshop TIFF reader. */
  public PhotoshopTiffReader() {
    super("Adobe Photoshop TIFF", new String[] {"tif", "tiff"});
    suffixSufficient = false;
    domains = new String[] {FormatTools.GRAPHICS_DOMAIN};
  }

  // -- IFormatReader API methods --

  /* @see loci.formats.IFormatReader#isThisType(RandomAccessInputStream) */
  public boolean isThisType(RandomAccessInputStream stream) throws IOException {
    TiffParser tp = new TiffParser(stream);
    return tp.getFirstIFD().containsKey(IMAGE_SOURCE_DATA);
  }

  /**
   * @see loci.formats.IFormatReader#openBytes(int, byte[], int, int, int, int)
   */
  public byte[] openBytes(int no, byte[] buf, int x, int y, int w, int h)
    throws FormatException, IOException
  {
    if (getSeries() == 0) return super.openBytes(no, buf, x, y, w, h);
    FormatTools.checkPlaneParameters(this, no, buf.length, x, y, w, h);

    tag.seek(layerOffset[(getSeries() - 1) * getSizeC()]);

    int bpp = FormatTools.getBytesPerPixel(getPixelType());

    if (compression[getSeries() - 1] == PACKBITS ||
      compression[getSeries() - 1] == ZIP)
    {
      Codec codec = compression[getSeries() - 1] == ZIP ? new ZlibCodec() :
        new PackbitsCodec();
      CodecOptions options = new CodecOptions();
      options.maxBytes = FormatTools.getPlaneSize(this) / getSizeC();
      ByteVector pix = new ByteVector();
      for (int c=0; c<getSizeC(); c++) {
        int index = channelOrder[getSeries() - 1][c];
        tag.seek(layerOffset[(getSeries() - 1) * getSizeC() + index]);
        byte[] p = codec.decompress(tag, options);
        pix.add(p);
      }
      RandomAccessInputStream plane =
        new RandomAccessInputStream(pix.toByteArray());
      readPlane(plane, x, y, w, h, buf);
      plane.close();
      pix = null;
    }
    else readPlane(tag, x, y, w, h, buf);

    return buf;
  }

  /* @see loci.formats.IFormatReader#close(boolean) */
  public void close(boolean fileOnly) throws IOException {
    super.close(fileOnly);
    if (!fileOnly) {
      if (tag != null) tag.close();
      tag = null;
      layerOffset = null;
      compression = null;
      layerNames = null;
    }
  }

  // -- Internal FormatReader API methods --

  /* @see loci.formats.FormatReader#initFile(String) */
  protected void initFile(String id) throws FormatException, IOException {
    super.initFile(id);

    CoreMetadata firstSeries = core[0];

    byte[] b = (byte[]) ifds.get(0).getIFDValue(IMAGE_SOURCE_DATA);
    if (b == null) return;

    tag = new RandomAccessInputStream(b);
    tag.order(isLittleEndian());

    String checkString = tag.readCString();

    String signature, type;
    int length;

    while (tag.getFilePointer() < tag.length() - 12 && tag.getFilePointer() > 0)
    {
      signature = tag.readString(4);
      type = tag.readString(4);
      length = tag.readInt();
      int skip = length % 4;
      if (skip != 0) skip = 4 - skip;

      if (type.equals("ryaL")) {
        int nLayers = (int) Math.abs(tag.readShort());

        compression = new int[nLayers];
        layerNames = new String[nLayers];

        core = new CoreMetadata[nLayers + 1];
        core[0] = firstSeries;
        channelOrder = new int[nLayers][];

        int[][] dataSize = new int[nLayers][];
        for (int layer=0; layer<nLayers; layer++) {
          int top = tag.readInt();
          int left = tag.readInt();
          int bottom = tag.readInt();
          int right = tag.readInt();

          core[layer + 1] = new CoreMetadata();
          core[layer + 1].sizeX = right - left;
          core[layer + 1].sizeY = bottom - top;
          core[layer + 1].pixelType = getPixelType();
          core[layer + 1].sizeC = tag.readShort();
          core[layer + 1].sizeZ = 1;
          core[layer + 1].sizeT = 1;
          core[layer + 1].imageCount = 1;
          core[layer + 1].rgb = isRGB();
          core[layer + 1].interleaved = isInterleaved();
          core[layer + 1].littleEndian = isLittleEndian();
          core[layer + 1].dimensionOrder = getDimensionOrder();

          if (layerOffset == null) {
            layerOffset = new long[nLayers * core[layer + 1].sizeC];
          }

          channelOrder[layer] = new int[core[layer + 1].sizeC];
          dataSize[layer] = new int[core[layer + 1].sizeC];
          for (int c=0; c<core[layer + 1].sizeC; c++) {
            int channelID = tag.readShort();
            if (channelID < 0) channelID = core[layer + 1].sizeC - 1;
            channelOrder[layer][channelID] = c;
            dataSize[layer][c] = tag.readInt();
          }

          tag.skipBytes(12);

          int len = tag.readInt();
          long fp = tag.getFilePointer();

          int mask = tag.readInt();
          if (mask != 0) tag.skipBytes(mask);
          int blending = tag.readInt();
          tag.skipBytes(blending);

          int nameLength = tag.read();
          int pad = nameLength % 4;
          if (pad != 0) pad = 4 - pad;
          layerNames[layer] = tag.readString(nameLength + pad);
          addGlobalMeta("Layer name #" + layer, layerNames[layer]);
          tag.skipBytes((int) (fp + len - tag.getFilePointer()));
        }

        for (int layer=0; layer<nLayers; layer++) {
          for (int c=0; c<core[layer + 1].sizeC; c++) {
            long startFP = tag.getFilePointer();
            compression[layer] = tag.readShort();
            layerOffset[(layer * core[layer + 1].sizeC) + c] =
              tag.getFilePointer();
            if (compression[layer] == ZIP) {
              layerOffset[(layer * core[layer + 1].sizeC) + c] =
                tag.getFilePointer();
              ZlibCodec codec = new ZlibCodec();
              codec.decompress(tag, null);
            }
            else if (compression[layer] == PACKBITS) {
              if (layer == 0) tag.skipBytes(256);
              else tag.skipBytes(192);
              layerOffset[(layer * core[layer + 1].sizeC) + c] =
                tag.getFilePointer();
              PackbitsCodec codec = new PackbitsCodec();
              CodecOptions options = new CodecOptions();
              options.maxBytes = core[layer + 1].sizeX * core[layer + 1].sizeY;
              codec.decompress(tag, options);
            }
            tag.seek(startFP + dataSize[layer][c]);
          }
        }
      }
      else tag.skipBytes(length + skip);
    }

    MetadataStore store =
      new FilterMetadata(getMetadataStore(), isMetadataFiltered());
    MetadataTools.populatePixels(store, this);
    store.setImageName("Merged", 0);
    for (int layer=0; layer<layerNames.length; layer++) {
      store.setImageName(layerNames[layer], layer + 1);
    }
  }

}
