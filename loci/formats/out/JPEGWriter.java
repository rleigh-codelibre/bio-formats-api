//
// JPEGWriter.java
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

package loci.formats.out;

import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.IOException;
import loci.formats.*;

/** JPEGWriter is the file format writer for JPEG files. */
public class JPEGWriter extends ImageIOWriter {

  // -- Constructor --

  public JPEGWriter() {
    super("Joint Photographic Experts Group",
      new String[] {"jpg", "jpeg", "jpe"}, "jpeg");
  }

  // -- IFormatWriter API methods --

  /* @see loci.formats.IFormatWriter#save(String, Image, boolean) */
  public void save(String id, Image image, boolean last)
    throws FormatException, IOException
  {
    BufferedImage img = (cm == null) ?
      ImageTools.makeBuffered(image) : ImageTools.makeBuffered(image, cm);
    int type = ImageTools.getPixelType(img);
    if (type == FormatTools.UINT16 || type == FormatTools.INT16) {
      throw new FormatException("16-bit data not supported.");
    }
    super.save(id, image, last);
  }

  /* @see loci.formats.IFormatWriter#getPixelTypes(String) */
  public int[] getPixelTypes(String id) throws FormatException, IOException {
    return new int[] {FormatTools.UINT8};
  }

  // -- Main method --

  public static void main(String[] args) throws FormatException, IOException {
    new JPEGWriter().testConvert(args);
  }

}
