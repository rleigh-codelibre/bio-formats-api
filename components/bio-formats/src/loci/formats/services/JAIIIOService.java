//
// JAIIIOService.java
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

package loci.formats.services;

import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;

import loci.common.services.Service;
import loci.common.services.ServiceException;

/**
 * Interface defining methods for reading data using JAI Image I/O.
 *
 * <dl><dt><b>Source code:</b></dt>
 * <dd><a href="https://skyking.microscopy.wisc.edu/trac/java/browser/trunk/components/bio-formats/src/loci/formats/services/JAIIIOService.java">Trac</a>,
 * <a href="https://skyking.microscopy.wisc.edu/svn/java/trunk/components/bio-format/src/loci/formats/services/JAIIIOService.java">SVN</a></dd></dl>
 */
public interface JAIIIOService extends Service {

  public void writeImage(OutputStream out, BufferedImage img, boolean lossless,
   int[] codeBlockSize, double quality) throws IOException, ServiceException;

  public BufferedImage readImage(InputStream in)
    throws IOException, ServiceException;

}
