//
// ReaderTest.java
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

package loci.formats.test;

import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;
import junit.framework.*;
import loci.formats.*;
import loci.formats.ome.OMEXMLMetadataStore;

/**
 * JUnit tester for Bio-Formats file format readers.
 * Details on failed tests are written to a log file, for easier processing.
 *
 * To test the framework, run this class from the command line with a command
 * line argument indicating the root path of data files to be tested. The
 * path will be scanned, and a list of files to test will be built.
 *
 * Unfortunately, it is not practical to construct one large JUnit test suite
 * with a static suite() method, because many datasets are spread across
 * multiple files. For example, a collection of 100 TIFF files numbered
 * tiff001.tif through tiff100.tif should only be tested once, rather than 100
 * times. To solve this problem, the list of files to test is whittled down
 * dynamically -- after each file is tested, the Bio-Formats library reports
 * all files that are part of that same dataset (i.e., essentially, a list of
 * all files just tested). These files are all removed from the list, ensuring
 * each dataset is only tested once.
 *
 * As such, this test case is not well suited for use with most JUnit tools,
 * such as junit.awtui.TestRunner, junit.swingui.TestRunner, or
 * junit.textui.TestRunner. If you are interested enough in unit testing to
 * have read this explanation, and have any thoughts or suggestions for
 * improvement, your thoughts would be most welcome.
 */
public class ReaderTest extends TestCase {

  // -- Static fields --

  private static boolean writeConfigFiles = false;
  private static boolean testXML = false; 
  private static float timeMultiplier = 1f;

  private static StringBuffer configLine;
  private static Vector configFiles = new Vector();
  private static ConfigurationFiles config = ConfigurationFiles.newInstance();
  private static FileWriter logFile;
  private String[] used;

  private static float averagePlaneAccess;
  private static int maxMemory;  // maximum measured memory usage
  private static int initialMemory;  // memory usage before opening the file
  private static int finalMemory;  // memory usage after closing file

  // -- Fields --

  private String id;
  private FileStitcher reader;

  // -- Constructor --

  public ReaderTest(String s) {
    super(s);
    throw new RuntimeException("Sorry, ReaderTest must be constructed with " +
      "a filename to read for performing the tests. See the class javadoc " +
      "for ReaderTest for more details.");
  }

  public ReaderTest(String s, String id) {
    super(s);
    this.id = id;
  }

  // -- ReaderTest API methods --

  /** Gets all constituent files in the tested dataset. */
  public String[] getUsedFiles() { return used; }

  /** Closes the reader. */
  public void close() {
    try {
      reader.close();
    }
    catch (Exception e) { }
  }

  // -- ReaderTest API methods - tests --

  /**
   * Checks the SizeX and SizeY dimensions against the actual dimensions of
   * the BufferedImages.
   */
  public void testBufferedImageDimensions() {
    boolean success = true;
    Runtime rt = Runtime.getRuntime();
    initialMemory = (int) ((rt.totalMemory() - rt.freeMemory()) >> 20);
    maxMemory = initialMemory;
    try {
      int planesRead = 0;
      reader.setId(id);
      long l1 = System.currentTimeMillis();
      for (int i=0; i<reader.getSeriesCount(); i++) {
        int usedMemory = (int) (rt.totalMemory() - rt.freeMemory()) >> 20;
        if (usedMemory > maxMemory) maxMemory = usedMemory;
        reader.setSeries(i);
        int imageCount = reader.getImageCount();
        int sizeX = reader.getSizeX();
        int sizeY = reader.getSizeY();

        for (int j=0; j<imageCount; j++) {
          BufferedImage b = reader.openImage(j);
          boolean failW = b.getWidth() != sizeX;
          boolean failH = b.getHeight() != sizeY;
          if (failW) writeLog(id + " failed width test");
          if (failH) writeLog(id + " failed height test");
          if (failW || failH) {
            success = false;
            j = imageCount;
            i = reader.getSeriesCount();
            break;
          }
        }
        planesRead += imageCount;
      }
      long l2 = System.currentTimeMillis();
      averagePlaneAccess = ((float) (l2 - l1)) / planesRead;
    }
    catch (Exception e) {
      if (FormatReader.debug) e.printStackTrace();
      success = false;
    }
    if (!success) writeLog(id + " failed BufferedImage test");
    try {
      reader.close();
      System.gc();
      Thread.sleep(100);
      System.gc();
    }
    catch (Exception e) { }
    finalMemory = (int) (rt.totalMemory() - rt.freeMemory()) >> 20;
    assertTrue(success);
  }

  /**
   * Checks the SizeX and SizeY dimensions against the actual dimensions of
   * the byte array returned by openBytes.
   */
  public void testByteArrayDimensions() {
    boolean success = true;
    try {
      reader.setId(id);
      for (int i=0; i<reader.getSeriesCount(); i++) {
        reader.setSeries(i);
        int imageCount = reader.getImageCount();
        int sizeX = reader.getSizeX();
        int sizeY = reader.getSizeY();
        int bytesPerPixel =
          FormatTools.getBytesPerPixel(reader.getPixelType());
        int sizeC = reader.getSizeC();
        boolean rgb = reader.isRGB();

        int expectedBytes = sizeX * sizeY * bytesPerPixel * (rgb ? sizeC : 1);

        for (int j=0; j<imageCount; j++) {
          byte[] b = reader.openBytes(j);
          if (b.length != expectedBytes) {
            success = false;
            j = imageCount;
            i = reader.getSeriesCount();
            break;
          }
        }
      }
    }
    catch (Exception e) {
      if (FormatReader.debug) e.printStackTrace();
      success = false;
    }
    if (!success) writeLog(id + " failed byte array test");
    try { reader.close(true); }
    catch (Exception e) { }
    assertTrue(success);
  }

  /**
   * Checks the reported thumbnail dimensions against the actual dimensions of
   * the thumbnail BufferedImages.
   */
  public void testThumbnailImageDimensions() {
    boolean success = true;
    try {
      int planesRead = 0;
      reader.setId(id);

      for (int i=0; i<reader.getSeriesCount(); i++) {
        reader.setSeries(i);
        int imageCount = reader.getImageCount();
        int sizeX = reader.getThumbSizeX();
        int sizeY = reader.getThumbSizeY();

        for (int j=0; j<imageCount; j++) {
          BufferedImage b = reader.openThumbImage(j);
          boolean failW = b.getWidth() != sizeX;
          boolean failH = b.getHeight() != sizeY;
          if (failW) writeLog(id + " failed thumbnail width test");
          if (failH) writeLog(id + " failed thumbnail height test");
          if (failW || failH) {
            success = false;
            j = imageCount;
            i = reader.getSeriesCount();
            break;
          }
        }
        planesRead += imageCount;
      }
    }
    catch (Exception e) {
      if (FormatReader.debug) e.printStackTrace();
      success = false;
    }
    if (!success) writeLog(id + " failed thumbnail BufferedImage test");
    assertTrue(success);
  }

  /**
   * Checks the reported thumbnail dimensions against the size of the array
   * returned by openThumbBytes.
   */
  public void testThumbnailArrayDimensions() {
    boolean success = true;
    try {
      reader.setId(id);
      for (int i=0; i<reader.getSeriesCount(); i++) {
        reader.setSeries(i);
        int imageCount = reader.getImageCount();
        int sizeX = reader.getThumbSizeX();
        int sizeY = reader.getThumbSizeY();
        int sizeC = reader.getRGBChannelCount();

        int expectedBytes = sizeX * sizeY * sizeC;

        for (int j=0; j<imageCount; j++) {
          byte[] b = reader.openThumbBytes(j);
          if (b.length != expectedBytes) {
            success = false;
            j = imageCount;
            i = reader.getSeriesCount();
            break;
          }
        }
      }
    }
    catch (Exception e) {
      if (FormatReader.debug) e.printStackTrace();
      success = false;
    }
    if (!success) writeLog(id + " failed thumbnail byte array test");
    try { reader.close(true); }
    catch (Exception e) { }
    assertTrue(success);
  }

  /**
   * Checks the SizeZ, SizeC, and SizeT dimensions against the
   * total image count.
   */
  public void testImageCount() {
    boolean success = true;
    try {
      reader.setId(id);
      for (int i=0; i<reader.getSeriesCount(); i++) {
        reader.setSeries(i);
        int imageCount = reader.getImageCount();
        int sizeZ = reader.getSizeZ();
        int sizeC = reader.getEffectiveSizeC();
        int sizeT = reader.getSizeT();
        if (success) success = imageCount == sizeZ * sizeC * sizeT;
        else break;
      }
    }
    catch (Exception e) {
      if (FormatReader.debug) e.printStackTrace();
      success = false;
    }
    if (!success) writeLog(id + " failed image count test");
    try { reader.close(true); }
    catch (Exception e) { }
    assertTrue(success);
  }

  /**
   * Checks that the OME-XML attribute values match the values of the core
   * metadata (Size*, DimensionOrder, etc.).
   */
  public void testOMEXML() {
    boolean success = true;
    try {
      OMEXMLMetadataStore store = new OMEXMLMetadataStore();
      store.createRoot();
      reader.setMetadataStore(store);
      reader.setId(id);

      for (int i=0; i<reader.getSeries(); i++) {
        reader.setSeries(i);
        int sizeX = reader.getSizeX();
        int sizeY = reader.getSizeY();
        int sizeZ = reader.getSizeZ();
        int sizeC = reader.getSizeC();
        int sizeT = reader.getSizeT();
        boolean bigEndian = !reader.isLittleEndian();
        String type = FormatTools.getPixelTypeString(reader.getPixelType());
        String dimensionOrder = reader.getDimensionOrder();

        Integer ii = new Integer(i);
        boolean failX = sizeX != store.getSizeX(ii).intValue();
        boolean failY = sizeY != store.getSizeY(ii).intValue();
        boolean failZ = sizeZ != store.getSizeZ(ii).intValue();
        boolean failC = sizeC != store.getSizeC(ii).intValue();
        boolean failT = sizeT != store.getSizeT(ii).intValue();
        boolean failBE = bigEndian != store.getBigEndian(ii).booleanValue();
        boolean failType = !type.equalsIgnoreCase(store.getPixelType(ii));
        boolean failDE = !dimensionOrder.equals(store.getDimensionOrder(ii));
        if (failX) writeLog(id + " failed OME-XML SizeX test");
        if (failY) writeLog(id + " failed OME-XML SizeY test");
        if (failZ) writeLog(id + " failed OME-XML SizeZ test");
        if (failC) writeLog(id + " failed OME-XML SizeC test");
        if (failT) writeLog(id + " failed OME-XML SizeT test");
        if (failBE) writeLog(id + " failed OME-XML BigEndian test");
        if (failType) writeLog(id + " failed OME-XML PixelType test");
        if (failDE) {
          writeLog(id + " failed OME-XML DimensionOrder test");
        }

        if (success) {
          success = failX || failY || failZ || failC ||
            failT || failBE || failType || failDE;
        }
        else break;
      }
    }
    catch (Exception e) {
      if (FormatReader.debug) e.printStackTrace();
      success = false;
    }
    if (!success) writeLog(id + " failed OME-XML sanity test");
    try { reader.close(true); }
    catch (Exception e) { }
    assertTrue(success);
  }

  /**
   * Checks that the core metadata values match those given in
   * the configuration file.  If there is no configuration file, this test
   * is not run.
   */
  public void testConsistent() {
    boolean success = true;
    try {
      reader.setId(id);
    }
    catch (Exception e) {
      writeLog(id + " failed consistent metadata test");
      if (FormatReader.debug) e.printStackTrace();
      assertTrue(false);
    }

    if (writeConfigFiles) {
      try {
        // assemble the config line
        configLine.append("\"");
        configLine.append(new Location(id).getName());
        configLine.append("\" total_series=");
        configLine.append(reader.getSeriesCount());
        for (int i=0; i<reader.getSeriesCount(); i++) {
          reader.setSeries(i);
          configLine.append(" [series=");
          configLine.append(i);
          configLine.append(" x=");
          configLine.append(reader.getSizeX());
          configLine.append(" y=");
          configLine.append(reader.getSizeY());
          configLine.append(" z=");
          configLine.append(reader.getSizeZ());
          configLine.append(" c=");
          configLine.append(reader.getSizeC());
          configLine.append(" t=");
          configLine.append(reader.getSizeT());
          configLine.append(" order=");
          configLine.append(reader.getDimensionOrder());
          configLine.append(" interleave=");
          configLine.append(reader.isInterleaved());
          configLine.append(" rgb=");
          configLine.append(reader.isRGB());
          configLine.append(" thumbx=");
          configLine.append(reader.getThumbSizeX());
          configLine.append(" thumby=");
          configLine.append(reader.getThumbSizeY());
          configLine.append(" type=");
          configLine.append(FormatTools.getPixelTypeString(
            reader.getPixelType()));
          configLine.append(" little=");
          configLine.append(reader.isLittleEndian());
          configLine.append("]");
        }
        configLine.append(" access=");
        configLine.append(averagePlaneAccess);
        configLine.append(" mem=");
        long len = 0;
        RandomAccessStream ras = new RandomAccessStream(id);
        configLine.append(ras.length());
        ras.close();
        configLine.append(" test=true\n");

        File f = new File(new Location(id).getParent(), ".bioformats");
        BufferedWriter w = new BufferedWriter(new FileWriter(f, true));
        w.write(configLine.toString());
        w.close();
      }
      catch (Exception e) {
        if (FormatReader.debug) e.printStackTrace();

        configLine = new StringBuffer();
        configLine.append("\"");
        configLine.append(new Location(id).getName());
        configLine.append("\" test=false\n");

        try {
          File f = new File(new Location(id).getParent(), ".bioformats");
          BufferedWriter w = new BufferedWriter(new FileWriter(f, true));
          w.write(configLine.toString());
          w.close();
        }
        catch (IOException exc) {
          if (FormatReader.debug) exc.printStackTrace();
          success = false;
        }
      }
    }
    else {
      int nSeries = 0;
      try {
        nSeries = reader.getSeries();
        if (nSeries != config.getNumSeries(id)) {
          success = false;
          writeLog(id + " failed consistent series count check");
        }
      }
      catch (Exception e) {
        if (FormatReader.debug) e.printStackTrace();
        success = false;
      }
      if (success) {
        try {
          for (int i=0; i<nSeries; i++) {
            config.setSeries(id, i);
            reader.setSeries(i);
            if (config.getWidth(id) != reader.getSizeX()) {
              success = false;
              writeLog(id + " failed consistent width check in series " + i);
            }
            if (config.getHeight(id) != reader.getSizeY()) {
              success = false;
              writeLog(id + " failed consistent height check in series " + i);
            }
            if (config.getZ(id) != reader.getSizeZ()) {
              success = false;
              writeLog(id + " failed consistent sizeZ check in series " + i);
            }
            if (config.getC(id) != reader.getSizeC()) {
              success = false;
              writeLog(id + " failed consistent sizeC check in series " + i);
            }
            if (config.getT(id) != reader.getSizeT()) {
              success = false;
              writeLog(id + " failed consistent sizeT check in series " + i);
            }
            if (!config.getDimOrder(id).equals(reader.getDimensionOrder())) {
              success = false;
              writeLog(id +
                " failed consistent dimension order check in series " + i);
            }
            if (config.isInterleaved(id) != reader.isInterleaved()) {
              success = false;
              writeLog(id +
                " failed consistent interleaving flag check in series " + i);
            }
            if (config.isRGB(id) != reader.isRGB()) {
              success = false;
              writeLog(id + " failed consistent RGB flag check in series " + i);
            }
            if (config.getThumbX(id) != reader.getThumbSizeX()) {
              success = false;
              writeLog(id +
                " failed consistent thumbnail width check in series " + i);
            }
            if (config.getThumbY(id) != reader.getThumbSizeY()) {
              success = false;
              writeLog(id +
                " failed consistent thumbnail height check in series " + i);
            }
            if (config.getPixelType(id) != reader.getPixelType()) {
              success = false;
              writeLog(id +
                " failed consistent pixel type check in series " + i);
            }
            if (config.isLittleEndian(id) != reader.isLittleEndian()) {
              success = false;
              writeLog(id +
                " failed consistent endianness flag check in series " + i);
            }
          }
        }
        catch (Exception e) {
          if (FormatReader.debug) e.printStackTrace();
          success = false;
        }
      }
    }
    assertTrue(success);
  }

  /** Check that the memory usage is acceptable. */
  public void testMemoryUsage() {
    boolean success = true;

    // we want the maximum usage to be no more than twice the file size
    if (maxMemory - initialMemory > 2*(config.getFileSize(id)+1)) {
      success = false;
      writeLog(id + " failed maximum memory usage test (used " +
        (maxMemory - initialMemory) + "MB; expected <= " +
        (2*config.getFileSize(id) + 1) + "MB)");
    }

    // check that the reader doesn't have any (significant) memory leaks
    if (finalMemory - initialMemory > 10) {
      success = false;
      writeLog(id + " failed memory leak test (" +
        (finalMemory - initialMemory) + "MB leaked)");
    }

    assertTrue(success);
  }

  /** Check that the average access time per plane is reasonable. */
  public void testAccessTime() {
    boolean success = true;
    if (averagePlaneAccess - timeMultiplier*config.getTimePerPlane(id) > 20.0) {
      success = false;
      writeLog(id + " failed consistent access time test (got " +
        averagePlaneAccess + " ms, expected " + config.getTimePerPlane(id) +
        " ms)");
    }
    assertTrue(success);
  }

  /**
   * Check that the used file list produced by each file in a set is the same.
   */
  public void testSaneUsedFiles() {
    try {
      reader.setId(id);
      String[] base = reader.getUsedFiles();
      Arrays.sort(base);

      FileStitcher fs = new FileStitcher();

      for (int i=0; i<base.length; i++) {
        fs.setId(base[i]);
        String[] comp = fs.getUsedFiles();
        Arrays.sort(comp);
        for (int j=0; j<comp.length; j++) {
          if (!comp[j].equals(base[j])) {
            writeLog(id + " failed sane used files test (" + base[i] + ")");
            assertTrue(false);
          }
        }
      }

      fs.close();
      assertTrue(true);
    }
    catch (Exception e) {
      if (FormatReader.debug) e.printStackTrace();
      writeLog(id + " failed sane used files test");
    }
    assertTrue(false);
  }

  /** Tests that OME-XML is valid. */
  public void testValidXML() {
    try { 
      OMEXMLMetadataStore store = new OMEXMLMetadataStore();
      store.createRoot();
      reader.setMetadataStore(store);
      reader.setId(id);
    
      String xml = ((OMEXMLMetadataStore) reader.getMetadataStore()).dumpXML(); 
      if (xml == null) writeLog(id + " failed OME-XML validation"); 
      assertTrue(xml != null); 
    }
    catch (Exception e) {
      writeLog(id + " failed OME-XML validation"); 
      e.printStackTrace(); 
      assertTrue(false);
    }
  }

  // -- TestCase API methods --

  /** Sets up the fixture. */
  protected void setUp() {
    reader = new FileStitcher();
    configLine = new StringBuffer();
  }

  /** Releases resources after tests have completed. */
  protected void tearDown() {
    try {
      reader.setId(id);
      used = reader.getUsedFiles();
      reader.close();
    }
    catch (FormatException fe) {
      if (FormatReader.debug) fe.printStackTrace();
    }
    catch (IOException io) {
      if (FormatReader.debug) io.printStackTrace();
    }
  }

  // -- Static ReaderTest API methods --

  /**
   * Creates a test suite for all ReaderTest tests, on the given file.
   * This method is patterned after the suite() method for use with a
   * TestRunner, but is distinct in that ReaderTest tests must be executed
   * on a particular input file.
   */
  public static TestSuite suite(String id) {
    TestSuite suite = new TestSuite();
    if (testXML) suite.addTest(new ReaderTest("testValidXML", id));
    else { 
      suite.addTest(new ReaderTest("testBufferedImageDimensions", id));
      if (!writeConfigFiles) {
        suite.addTest(new ReaderTest("testByteArrayDimensions", id));
        suite.addTest(new ReaderTest("testThumbnailImageDimensions", id));
        suite.addTest(new ReaderTest("testThumbnailArrayDimensions", id));
        suite.addTest(new ReaderTest("testImageCount", id));
        suite.addTest(new ReaderTest("testOMEXML", id));
        suite.addTest(new ReaderTest("testSaneUsedFiles", id));
        suite.addTest(new ReaderTest("testValidXML", id)); 
      }
      if (config.initialized(id) || writeConfigFiles) {
        suite.addTest(new ReaderTest("testConsistent", id));
      }
      if (config.initialized(id) && !writeConfigFiles) {
        suite.addTest(new ReaderTest("testMemoryUsage", id));
        suite.addTest(new ReaderTest("testAccessTime", id));
      }
    }
    return suite;
  }

  /**
   * Determines if the given filename is a "bad" file.
   * Bad files are skipped rather than tested.
   */
  public static boolean isBadFile(String file) {
    for (int i=0; i<configFiles.size(); i++) {
      try {
        String s = (String) configFiles.get(i);
        if (!config.isParsed(s)) config.addFile(s);
      }
      catch (IOException e) {
        if (FormatReader.debug) e.printStackTrace();
      }
    }
    return !config.testFile(file) && !file.endsWith(".bioformats");
  }

  /** Recursively generates a list of files to test. */
  public static void getFiles(String root, Vector files) {
    Location f = new Location(root);
    String[] subs = f.list();
    f = null;
    Arrays.sort(subs);

    // make sure that if a config file exists, it is first on the list
    for (int i=0; i<subs.length; i++) {
      if (subs[i].endsWith(".bioformats") && i != 0) {
        String tmp = subs[0];
        subs[0] = subs[i];
        subs[i] = tmp;
        break;
      }
    }

    if (subs == null) {
      System.out.println("Invalid directory: " + root);
      return;
    }
    ImageReader ir = new ImageReader();
    Vector similarFiles = new Vector();
    for (int i=0; i<subs.length; i++) {
      if (FormatReader.debug) debug("Checking file " + subs[i]);
      subs[i] = root + (root.endsWith(File.separator) ? "" : File.separator) +
        subs[i];
      if (isBadFile(subs[i]) || similarFiles.contains(subs[i]) ||
        similarFiles.contains(new Location(root, subs[i]).getAbsolutePath()))
      {
        if (FormatReader.debug) debug(subs[i] + " is a bad file");
        String[] matching = new FilePattern(subs[i]).getFiles();
        for (int j=0; j<matching.length; j++) {
          similarFiles.add(new Location(root, matching[j]).getAbsolutePath());
        }
        continue;
      }
      Location file = new Location(subs[i]);

      if (file.isDirectory()) getFiles(subs[i], files);
      else if (file.getName().equals(".bioformats")) {
        // special config file for the test suite
        configFiles.add(file.getAbsolutePath());
      }
      else {
        if (ir.isThisType(subs[i])) {
          if (FormatReader.debug) debug("Adding " + subs[i]);
          files.add(file.getAbsolutePath());
        }
        else if (FormatReader.debug) debug(subs[i] + " has invalid type");
      }
      file = null;
    }
  }

  /** Writes the given message to the log file. */
  public static void writeLog(String s) {
    if (logFile == null) {
      try {
        String date = new Date().toString().replaceAll(":", "-");
        logFile = new FileWriter("bio-formats-test-" + date + ".log");
        logFile.flush();
      }
      catch (IOException io) {
        if (FormatReader.debug) io.printStackTrace();
      }
    }
    try {
      logFile.write(s + "\n");
      logFile.flush();
    }
    catch (IOException exc) {
      if (FormatReader.debug) exc.printStackTrace();
    }
  }

  public static void debug(String s) { System.out.println(s); }

  // -- Main method --

  public static void main(String[] args) {
    if (args.length > 0) {
      for (int i=1; i<args.length; i++) {
        if (args[i].equals("-config")) ReaderTest.writeConfigFiles = true;
        else if (args[i].equals("-debug")) FormatReader.setDebug(true);
        else if (args[i].equals("-xmlonly")) ReaderTest.testXML = true; 
        else if (args[i].equals("-time")) {
          ReaderTest.timeMultiplier = Float.parseFloat(args[i+1]);
        }
      }
    }
    Vector files = new Vector();
    if (args == null || args.length == 0) {
      System.out.println(
        "Please specify root folder to search for data files.");
      System.exit(1);
    }
    System.out.print("Building file list...");
    if (FormatReader.debug) System.out.println();
    getFiles(new Location(args[0]).getAbsolutePath(), files);
    System.out.println(files.size() + " found.");
    while (files.size() > 0) {
      String id = (String) files.elementAt(0);
      String pattern = FilePattern.findPattern(new Location(id));
      if (pattern == null) pattern = id;
      System.out.println("Testing " + pattern);
      TestResult result = new TestResult();
      TestSuite suite = suite(id);
      suite.run(result);
      int total = result.runCount();
      int failed = result.failureCount();
      float failPercent = (float) (100 * ((double) failed / (double) total));
      System.out.println(id + " - " + failed + " failures in " +
        total + " tests (" + failPercent + "% failed)");

      // remove files part of the just-tested dataset from the list
      ReaderTest test = (ReaderTest) suite.testAt(0);
      String[] used = test.getUsedFiles();
      if (used == null) {
        System.out.println("Warning: used files list is null for " + id);

        used = new FilePattern(pattern).getFiles();
        if (used != null) {
          for (int i=0; i<used.length; i++) {
            if (FormatReader.debug) System.out.println("Removing " + used[i]);
            files.removeElement(used[i]);
          }
        }
        else files.removeElementAt(0);
      }
      else {
        for (int i=0; i<used.length; i++) {
          if (FormatReader.debug) System.out.println("Removing " + used[i]);
          files.removeElement(used[i]);
        }
      }
      while (files.contains(id)) files.remove(id);

      test.close();
    }
  }
}
