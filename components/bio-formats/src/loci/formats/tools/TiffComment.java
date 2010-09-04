//
// TiffComment.java
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

package loci.formats.tools;

import java.io.IOException;

import loci.formats.FormatException;
import loci.formats.tiff.TiffParser;

/**
 * Extracts the comment from the first IFD of the given TIFF file(s).
 *
 * <dl><dt><b>Source code:</b></dt>
 * <dd><a href="http://dev.loci.wisc.edu/trac/java/browser/trunk/components/bio-formats/src/loci/formats/tools/TiffComment.java">Trac</a>,
 * <a href="http://dev.loci.wisc.edu/svn/java/trunk/components/bio-formats/src/loci/formats/tools/TiffComment.java">SVN</a></dd></dl>
 */
public class TiffComment {

  public static void main(String[] args) throws FormatException, IOException {
    if (args.length == 0) {
      System.out.println("Usage: tiffcomment [-edit] file1 [file2 ...]");
      return;
    }

    // parse flags
    boolean edit = false;
    for (int i=0; i<args.length; i++) {
      if (!args[i].startsWith("-")) continue;

      if (args[i].equals("-edit")) edit = true;
      else System.out.println("Warning: unknown flag: " + args[i]);
    }

    // process files
    for (int i=0; i<args.length; i++) {
      if (args[i].startsWith("-")) continue;

      if (edit) EditTiffG.openFile(args[i]);
      else {
        String comment = new TiffParser(args[i]).getComment();
        System.out.println(comment == null ?
          args[i] + ": no TIFF comment found." : comment);
      }
    }
  }

}
