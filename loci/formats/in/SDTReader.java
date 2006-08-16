//
// SDTReader.java
//

/*
LOCI Bio-Formats package for reading and converting biological file formats.
Copyright (C) 2005-@year@ Melissa Linkert, Curtis Rueden, Chris Allan
and Eric Kjellman.

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

import java.awt.image.BufferedImage;
import java.io.*;
import loci.formats.*;

/**
 * SDTReader is the file format reader for
 * Becker &amp; Hickl SPC-Image SDT files.
 *
 * This importer is based on MATLAB code originally by Long Yan.
 *
 * @author Curtis Rueden ctrueden at wisc.edu
 */
public class SDTReader extends FormatReader {

  // -- Constants --

  /** Number of time bins in the decay curve. */
  protected static final int TIME_BINS = 64;


  // -- Fields --

  /** Current file. */
  protected RandomAccessStream in;

  /** Length in bytes of current file. */
  protected int fileLen;

  /** Number of images in current SDT file. */
  protected int numImages;

  /** Offset to binary data. */
  protected int offset;

  /** Dimensions of the current SDT file's images. */
  protected int width = 128, height = 128;


  // -- Constructor --

  /** Constructs a new SDT reader. */
  public SDTReader() { super("SPC-Image SDT", "sdt"); }


  // -- FormatReader API methods --

  /** Checks if the given block is a valid header for an SDT file. */
  public boolean isThisType(byte[] block) { return false; }

  /** Determines the number of images in the given SDT file. */
  public int getImageCount(String id) throws FormatException, IOException {
    if (!id.equals(currentId)) initFile(id);
    return numImages;
  }

  /** Checks if the images in the file are RGB. */
  public boolean isRGB(String id) throws FormatException, IOException {
    return false;
  }

  /** Get the size of the X dimension. */
  public int getSizeX(String id) throws FormatException, IOException {
    if (!id.equals(currentId)) initFile(id);
    return width;
  }

  /** Get the size of the Y dimension. */
  public int getSizeY(String id) throws FormatException, IOException {
    if (!id.equals(currentId)) initFile(id);
    return height;
  }

  /** Get the size of the Z dimension. */
  public int getSizeZ(String id) throws FormatException, IOException {
    if (!id.equals(currentId)) initFile(id);
    return numImages;
  }

  /** Get the size of the C dimension. */
  public int getSizeC(String id) throws FormatException, IOException {
    return 1;
  }

  /** Get the size of the T dimension. */
  public int getSizeT(String id) throws FormatException, IOException {
    return 1;
  }

  /** Return true if the data is in little-endian format. */
  public boolean isLittleEndian(String id) throws FormatException, IOException
  {
    return true;
  }

  /**
   * Return a five-character string representing the dimension order
   * within the file.
   */
  public String getDimensionOrder(String id) throws FormatException, IOException
  {
    return "XYZTC";
  }

  /** Obtains the specified image from the given SDT file as a byte array. */
  public byte[] openBytes(String id, int no)
    throws FormatException, IOException
  {
    if (!id.equals(currentId)) initFile(id);

    if (no < 0 || no >= getImageCount(id)) {
      throw new FormatException("Invalid image number: " + no);
    }

    in.seek(offset + 2 * width * height * TIME_BINS * no);
    short[] data = new short[width * height];

    for (int y=0; y<height; y++) {
      for (int x=0; x<width; x++) {
        int ndx = width * y + x;
        int sum = 0;
        for (int decay=0; decay<TIME_BINS; decay++) {
          sum += in.readShort();
        }
        data[ndx] = (short) sum;
      }
    }

    byte[] p = new byte[data.length * 2];
    for (int i=0; i<data.length; i++) {
      byte[] b = DataTools.shortToBytes(data[i], true);
      p[2*i] = b[0];
      p[2*i + 1] = b[1];
    }
    return p;
  }

  /** Obtains the specified image from the given SDT file. */
  public BufferedImage openImage(String id, int no)
    throws FormatException, IOException
  {
    return ImageTools.makeImage(openBytes(id, no), width, height, 1, false,
      2, true);
  }

  /** Closes any open files. */
  public void close() throws FormatException, IOException {
    if (in != null) in.close();
    in = null;
    currentId = null;
  }

  /** Initializes the given SDT file. */
  protected void initFile(String id) throws FormatException, IOException {
    super.initFile(id);
    in = new RandomAccessStream(id);
    in.order(true);

    // skip 14 byte header
    in.skipBytes(14);

    // read offset
    offset = in.readShort() + 22;

    // skip to data
    in.seek(offset);

    // compute number of image planes
    numImages = (int) ((in.length() - offset) / (2 * 64 * width * height));

    MetadataStore store = getMetadataStore(id);
    store.setPixels(new Integer(getSizeX(id)), new Integer(getSizeY(id)),
      new Integer(getSizeZ(id)), new Integer(getSizeC(id)),
      new Integer(getSizeT(id)), "int16", new Boolean(!isLittleEndian(id)),
      getDimensionOrder(id), null);
  }


  // -- Main method --

  public static void main(String[] args) throws FormatException, IOException {
    new SDTReader().testRead(args);
  }

}
