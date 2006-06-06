//
// QTWriter.java
//

/*
LOCI Bio-Formats package for reading and converting biological file formats.
Copyright (C) 2005-2006 Melissa Linkert, Curtis Rueden and Eric Kjellman.

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

package loci.formats;

import java.awt.Image;
import java.awt.image.*;
import java.io.*;
import java.util.Vector;


/**
 * QTWriter is the file format writer for uncompressed QuickTime movie files.
 *
 * @author Melissa Linkert linkert at cs.wisc.edu
 */

public class QTWriter extends FormatWriter {

  /** Current file. */
  protected RandomAccessFile out;

  /** Number of planes written. */
  protected int numWritten;

  /** Seek to this offset to update the total number of pixel bytes. */
  protected long byteCountOffset;

  /** Total number of pixel bytes. */
  protected int numBytes;

  /** Vector of plane offsets. */
  protected Vector offsets;

  /** Time the file was created. */
  protected int created;

  // -- Constructor --

  public QTWriter() { super("QuickTime", "mov"); }


  // -- FormatWriter API methods --

  /**
   * Saves the given image to the specified (possibly already open) file.
   * If this image is the last one in the file, the last flag must be set.
   */
  public void save(String id, Image image, boolean last)
    throws FormatException, IOException
  {
    if (image == null) {
      throw new FormatException("Image is null");
    }

    BufferedImage img = (cm == null) ?
      ImageTools.makeBuffered(image) : ImageTools.makeBuffered(image, cm);

    // get the width and height of the image
    int width = img.getWidth();
    int height = img.getHeight();

    // retrieve pixel data for this plane
    byte[][] byteData = ImageTools.getBytes(img);

    // need to check if the width is a multiple of 8
    // if it is, great; if not, we need to pad each scanline with enough
    // bytes to make the width a multiple of 8

    int pad = width % 4;
    pad = (4 - pad) % 4;

    byte[][] temp = byteData;
    byteData = new byte[temp.length][temp[0].length + height*pad];

    int newScanline = height - 1;

    for (int oldScanline=0; oldScanline<height; oldScanline++) {
      for (int k=0; k<temp.length; k++) {
        System.arraycopy(temp[k], oldScanline*width, byteData[k],
          oldScanline*(width+pad), width);

        // add padding bytes

        for (int i=0; i<pad; i++) {
          byteData[k][oldScanline*(width+pad) + width + i] = 0;
        }
      }
    }

    // invert each pixel
    // this will makes the colors look right in other readers (e.g. xine),
    // but needs to be reversed in QTReader

    if (byteData.length == 1) {
      for (int i=0; i<byteData.length; i++) {
        for (int k=0; k<byteData[0].length; k++) {
          byteData[i][k] = (byte) (255 - byteData[i][k]);
        }
      }
    }

    if (!id.equals(currentId)) {
      // -- write the header --

      offsets = new Vector();
      currentId = id;
      out = new RandomAccessFile(id, "rw");
      created = (int) System.currentTimeMillis();
      numWritten = 1;

      // -- write the first header --

      DataTools.writeInt(out, 8, false);
      DataTools.writeString(out, "wide");

      // -- write the first plane of pixel data (mdat) --

      numBytes = byteData[0].length * byteData.length;

      byteCountOffset = out.getFilePointer();
      DataTools.writeInt(out, numBytes + 8, false);
      DataTools.writeString(out, "mdat");

      for (int i=0; i<byteData[0].length; i++) {
        for (int j=0; j<byteData.length; j++) {
          out.write(byteData[j][i]);
        }
      }

      offsets.add(new Integer(16));
    }
    else {
      // update the number of pixel bytes written
      int planeOffset = numBytes;
      numBytes += (byteData.length * byteData[0].length);
      out.seek(byteCountOffset);
      DataTools.writeInt(out, numBytes + 8, false);

      // write this plane's pixel data
      out.seek(out.length());

      for (int i=0; i<byteData[0].length; i++) {
        for (int j=0; j<byteData.length; j++) {
          out.write(byteData[j][i]);
        }
      }

      offsets.add(new Integer(planeOffset + 16));
      numWritten++;
    }

    if (last) {
      int timeScale = 100;
      int duration = numWritten * (timeScale / fps);
      int bitsPerPixel = (byteData.length > 1) ? 24 : 40;
      int channels = (bitsPerPixel == 40) ? 1 : 3;

      // -- write moov atom --

      int atomLength = 685 + 8*numWritten;
      DataTools.writeInt(out, atomLength, false);
      DataTools.writeString(out, "moov");

      // -- write mvhd atom --

      DataTools.writeInt(out, 108, false);
      DataTools.writeString(out, "mvhd");
      DataTools.writeShort(out, 0, false); // version
      DataTools.writeShort(out, 0, false); // flags
      DataTools.writeInt(out, created, false); // creation time
      DataTools.writeInt(out, (int) System.currentTimeMillis(), false);
      DataTools.writeInt(out, timeScale, false); // time scale
      DataTools.writeInt(out, duration, false); // duration
      out.write(new byte[] {0, 1, 0, 0});  // preferred rate & volume
      out.write(new byte[] {0, -1, 0, 0, 0, 0, 0, 0, 0, 0}); // reserved

      // 3x3 matrix - unsure of significance

      DataTools.writeInt(out, 1, false);
      DataTools.writeInt(out, 0, false);
      DataTools.writeInt(out, 0, false);
      DataTools.writeInt(out, 0, false);
      DataTools.writeInt(out, 1, false);
      DataTools.writeInt(out, 0, false);
      DataTools.writeInt(out, 0, false);
      DataTools.writeInt(out, 0, false);
      DataTools.writeInt(out, 16384, false);

      DataTools.writeShort(out, 0, false); // not sure what this is
      DataTools.writeInt(out, 0, false); // preview duration
      DataTools.writeInt(out, 0, false); // preview time
      DataTools.writeInt(out, 0, false); // poster time
      DataTools.writeInt(out, 0, false); // selection time
      DataTools.writeInt(out, 0, false); // selection duration
      DataTools.writeInt(out, 0, false); // current time
      DataTools.writeInt(out, 2, false); // next track's id

      // -- write trak atom --

      atomLength -= 116;
      DataTools.writeInt(out, atomLength, false);
      DataTools.writeString(out, "trak");

      // -- write tkhd atom --

      DataTools.writeInt(out, 92, false);
      DataTools.writeString(out, "tkhd");
      DataTools.writeShort(out, 0, false); // version
      DataTools.writeShort(out, 15, false); // flags

      DataTools.writeInt(out, created, false); // creation time
      DataTools.writeInt(out, (int) System.currentTimeMillis(), false);
      DataTools.writeInt(out, 1, false); // track id
      DataTools.writeInt(out, 0, false); // reserved

      DataTools.writeInt(out, duration, false); // duration
      DataTools.writeInt(out, 0, false); // reserved
      DataTools.writeInt(out, 0, false); // reserved
      DataTools.writeShort(out, 0, false); // reserved

      DataTools.writeInt(out, 0, false); // unknown
      // 3x3 matrix - unsure of significance

      DataTools.writeInt(out, 1, false);
      DataTools.writeInt(out, 0, false);
      DataTools.writeInt(out, 0, false);
      DataTools.writeInt(out, 0, false);
      DataTools.writeInt(out, 1, false);
      DataTools.writeInt(out, 0, false);
      DataTools.writeInt(out, 0, false);
      DataTools.writeInt(out, 0, false);
      DataTools.writeInt(out, 16384, false);

      DataTools.writeInt(out, width, false); // image width
      DataTools.writeInt(out, height, false); // image height
      DataTools.writeShort(out, 0, false); // reserved


      // -- write edts atom --

      DataTools.writeInt(out, 36, false);
      DataTools.writeString(out, "edts");

      // -- write elst atom --

      DataTools.writeInt(out, 28, false);
      DataTools.writeString(out, "elst");

      DataTools.writeShort(out, 0, false); // version
      DataTools.writeShort(out, 0, false); // flags
      DataTools.writeInt(out, 1, false); // number of entries in the table
      DataTools.writeInt(out, duration, false); // duration
      DataTools.writeShort(out, 0, false); // time
      DataTools.writeInt(out, 1, false); // rate
      DataTools.writeShort(out, 0, false); // unknown

      // -- write mdia atom --

      atomLength -= 136;
      DataTools.writeInt(out, atomLength, false);
      DataTools.writeString(out, "mdia");

      // -- write mdhd atom --

      DataTools.writeInt(out, 32, false);
      DataTools.writeString(out, "mdhd");

      DataTools.writeShort(out, 0, false); // version
      DataTools.writeShort(out, 0, false); // flags
      DataTools.writeInt(out, created, false); // creation time
      DataTools.writeInt(out, (int) System.currentTimeMillis(), false);
      DataTools.writeInt(out, timeScale, false); // time scale
      DataTools.writeInt(out, duration, false); // duration
      DataTools.writeShort(out, 0, false); // language
      DataTools.writeShort(out, 0, false); // quality

      // -- write hdlr atom --

      DataTools.writeInt(out, 58, false);
      DataTools.writeString(out, "hdlr");

      DataTools.writeShort(out, 0, false); // version
      DataTools.writeShort(out, 0, false); // flags
      DataTools.writeString(out, "mhlr");
      DataTools.writeString(out, "vide");
      DataTools.writeString(out, "appl");
      out.write(new byte[] {16, 0, 0, 0, 0, 1, 1, 11, 25});
      DataTools.writeString(out, "Apple Video Media Handler");

      // -- write minf atom --

      atomLength -= 98;
      DataTools.writeInt(out, atomLength, false);
      DataTools.writeString(out, "minf");

      // -- write vmhd atom --

      DataTools.writeInt(out, 20, false);
      DataTools.writeString(out, "vmhd");

      DataTools.writeShort(out, 0, false); // version
      DataTools.writeShort(out, 1, false); // flags
      DataTools.writeShort(out, 64, false); // graphics mode
      DataTools.writeShort(out, 32768, false);  // opcolor 1
      DataTools.writeShort(out, 32768, false);  // opcolor 2
      DataTools.writeShort(out, 32768, false);  // opcolor 3

      // -- write hdlr atom --

      DataTools.writeInt(out, 57, false);
      DataTools.writeString(out, "hdlr");

      DataTools.writeShort(out, 0, false); // version
      DataTools.writeShort(out, 0, false); // flags
      DataTools.writeString(out, "dhlr");
      DataTools.writeString(out, "alis");
      DataTools.writeString(out, "appl");
      out.write(new byte[] {16, 0, 0, 1, 0, 1, 1, 31, 24});
      DataTools.writeString(out, "Apple Alias Data Handler");

      // -- write dinf atom --

      DataTools.writeInt(out, 36, false);
      DataTools.writeString(out, "dinf");

      // -- write dref atom --

      DataTools.writeInt(out, 28, false);
      DataTools.writeString(out, "dref");

      DataTools.writeShort(out, 0, false); // version
      DataTools.writeShort(out, 0, false); // flags
      DataTools.writeShort(out, 0, false); // version 2
      DataTools.writeShort(out, 1, false); // flags 2
      out.write(new byte[] {0, 0, 0, 12});
      DataTools.writeString(out, "alis");
      DataTools.writeShort(out, 0, false); // version 3
      DataTools.writeShort(out, 1, false); // flags 3

      // -- write stbl atom --

      atomLength -= 121;
      DataTools.writeInt(out, atomLength, false);
      DataTools.writeString(out, "stbl");

      // -- write stsd atom --

      DataTools.writeInt(out, 118, false);
      DataTools.writeString(out, "stsd");

      DataTools.writeShort(out, 0, false); // version
      DataTools.writeShort(out, 0, false); // flags
      DataTools.writeInt(out, 1, false); // number of entries in the table
      out.write(new byte[] {0, 0, 0, 102});
      DataTools.writeString(out, "raw "); // codec
      out.write(new byte[] {0, 0, 0, 0, 0, 0});  // reserved
      DataTools.writeShort(out, 1, false); // data reference
      DataTools.writeShort(out, 1, false); // version
      DataTools.writeShort(out, 1, false); // revision
      DataTools.writeString(out, "appl");
      DataTools.writeInt(out, 0, false); // temporal quality
      DataTools.writeInt(out, 768, false); // spatial quality
      DataTools.writeShort(out, width, false); // image width
      DataTools.writeShort(out, height, false); // image height
      out.write(new byte[] {0, 72, 0, 0}); // horizontal dpi
      out.write(new byte[] {0, 72, 0, 0}); // vertical dpi
      DataTools.writeInt(out, 0, false); // data size
      DataTools.writeShort(out, 1, false); // frames per sample
      DataTools.writeShort(out, 12, false); // length of compressor name
      DataTools.writeString(out, "Uncompressed"); // compressor name
      DataTools.writeInt(out, bitsPerPixel, false); // unknown
      DataTools.writeInt(out, bitsPerPixel, false); // unknown
      DataTools.writeInt(out, bitsPerPixel, false); // unknown
      DataTools.writeInt(out, bitsPerPixel, false); // unknown
      DataTools.writeInt(out, bitsPerPixel, false); // unknown
      DataTools.writeShort(out, bitsPerPixel, false); // bits per pixel
      DataTools.writeInt(out, 65535, false); // ctab ID
      out.write(new byte[] {12, 103, 97, 108}); // gamma
      out.write(new byte[] {97, 1, -52, -52, 0, 0, 0, 0}); // unknown

      // -- write stts atom --

      DataTools.writeInt(out, 24, false);
      DataTools.writeString(out, "stts");

      DataTools.writeShort(out, 0, false); // version
      DataTools.writeShort(out, 0, false); // flags
      DataTools.writeInt(out, 1, false); // number of entries in the table
      DataTools.writeInt(out, numWritten, false); // number of planes
      DataTools.writeInt(out, (timeScale / fps), false); // frames per second

      // -- write stsc atom --

      DataTools.writeInt(out, 28, false);
      DataTools.writeString(out, "stsc");

      DataTools.writeShort(out, 0, false); // version
      DataTools.writeShort(out, 0, false); // flags
      DataTools.writeInt(out, 1, false); // number of entries in the table
      DataTools.writeInt(out, 1, false); // chunk
      DataTools.writeInt(out, 1, false); // samples
      DataTools.writeInt(out, 1, false); // id

      // -- write stsz atom --

      DataTools.writeInt(out, 20 + 4*numWritten, false);
      DataTools.writeString(out, "stsz");

      DataTools.writeShort(out, 0, false); // version
      DataTools.writeShort(out, 0, false); // flags
      DataTools.writeInt(out, 0, false); // sample size
      DataTools.writeInt(out, numWritten, false); // number of planes
      for (int i=0; i<numWritten; i++) {
        // sample size
        DataTools.writeInt(out, channels*height*(width+pad), false);
      }

      // -- write stco atom --

      DataTools.writeInt(out, 16 + 4*numWritten, false);
      DataTools.writeString(out, "stco");

      DataTools.writeShort(out, 0, false); // version
      DataTools.writeShort(out, 0, false); // flags
      DataTools.writeInt(out, numWritten, false); // number of planes
      for (int i=0; i<numWritten; i++) {
        // write the plane offset
        DataTools.writeInt(out, ((Integer) offsets.get(i)).intValue(), false);
      }

      out.close();
    }
  }

  /** Reports whether the writer can save multiple images to a single file. */
  public boolean canDoStacks(String id) { return true; }


  // -- Main method --

  public static void main(String[] args) throws IOException, FormatException {
    new QTWriter().testConvert(args);
  }

}
