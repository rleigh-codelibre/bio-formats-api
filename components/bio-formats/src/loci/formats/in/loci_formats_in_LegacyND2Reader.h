/*
 * #%L
 * OME Bio-Formats package for reading and converting biological file formats.
 * %%
 * Copyright (C) 2005 - 2012 Open Microscopy Environment:
 *   - Board of Regents of the University of Wisconsin-Madison
 *   - Glencoe Software, Inc.
 *   - University of Dundee
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 2 of the 
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public 
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-2.0.html>.
 * #L%
 */

/* DO NOT EDIT THIS FILE - it is machine generated */
#include <jni.h>
/* Header for class loci_formats_in_LegacyND2Reader */

#ifndef _Included_loci_formats_in_LegacyND2Reader
#define _Included_loci_formats_in_LegacyND2Reader
#ifdef __cplusplus
extern "C" {
#endif
#undef loci_formats_in_LegacyND2Reader_THUMBNAIL_DIMENSION
#define loci_formats_in_LegacyND2Reader_THUMBNAIL_DIMENSION 128L
#undef loci_formats_in_LegacyND2Reader_WIDE_FIELD
#define loci_formats_in_LegacyND2Reader_WIDE_FIELD 0L
#undef loci_formats_in_LegacyND2Reader_BRIGHT_FIELD
#define loci_formats_in_LegacyND2Reader_BRIGHT_FIELD 1L
#undef loci_formats_in_LegacyND2Reader_LASER_SCAN_CONFOCAL
#define loci_formats_in_LegacyND2Reader_LASER_SCAN_CONFOCAL 2L
#undef loci_formats_in_LegacyND2Reader_SPIN_DISK_CONFOCAL
#define loci_formats_in_LegacyND2Reader_SPIN_DISK_CONFOCAL 3L
#undef loci_formats_in_LegacyND2Reader_SWEPT_FIELD_CONFOCAL
#define loci_formats_in_LegacyND2Reader_SWEPT_FIELD_CONFOCAL 4L
#undef loci_formats_in_LegacyND2Reader_MULTI_PHOTON
#define loci_formats_in_LegacyND2Reader_MULTI_PHOTON 5L
/*
 * Class:     loci_formats_in_LegacyND2Reader
 * Method:    openFile
 * Signature: (Ljava/lang/String;)V
 */
JNIEXPORT void JNICALL Java_loci_formats_in_LegacyND2Reader_openFile
  (JNIEnv *, jobject, jstring);

/*
 * Class:     loci_formats_in_LegacyND2Reader
 * Method:    getNumSeries
 * Signature: ()I
 */
JNIEXPORT jint JNICALL Java_loci_formats_in_LegacyND2Reader_getNumSeries
  (JNIEnv *, jobject);

/*
 * Class:     loci_formats_in_LegacyND2Reader
 * Method:    getWidth
 * Signature: (I)I
 */
JNIEXPORT jint JNICALL Java_loci_formats_in_LegacyND2Reader_getWidth
  (JNIEnv *, jobject, jint);

/*
 * Class:     loci_formats_in_LegacyND2Reader
 * Method:    getHeight
 * Signature: (I)I
 */
JNIEXPORT jint JNICALL Java_loci_formats_in_LegacyND2Reader_getHeight
  (JNIEnv *, jobject, jint);

/*
 * Class:     loci_formats_in_LegacyND2Reader
 * Method:    getZSlices
 * Signature: (I)I
 */
JNIEXPORT jint JNICALL Java_loci_formats_in_LegacyND2Reader_getZSlices
  (JNIEnv *, jobject, jint);

/*
 * Class:     loci_formats_in_LegacyND2Reader
 * Method:    getTFrames
 * Signature: (I)I
 */
JNIEXPORT jint JNICALL Java_loci_formats_in_LegacyND2Reader_getTFrames
  (JNIEnv *, jobject, jint);

/*
 * Class:     loci_formats_in_LegacyND2Reader
 * Method:    getChannels
 * Signature: (I)I
 */
JNIEXPORT jint JNICALL Java_loci_formats_in_LegacyND2Reader_getChannels
  (JNIEnv *, jobject, jint);

/*
 * Class:     loci_formats_in_LegacyND2Reader
 * Method:    getBytesPerPixel
 * Signature: (I)I
 */
JNIEXPORT jint JNICALL Java_loci_formats_in_LegacyND2Reader_getBytesPerPixel
  (JNIEnv *, jobject, jint);

/*
 * Class:     loci_formats_in_LegacyND2Reader
 * Method:    getImage
 * Signature: (IIII)[B
 */
JNIEXPORT jbyteArray JNICALL Java_loci_formats_in_LegacyND2Reader_getImage
  (JNIEnv *, jobject, jbyteArray, jint, jint, jint, jint);

/*
 * Class:     loci_formats_in_LegacyND2Reader
 * Method:    getDX
 * Signature: (IIII)D
 */
JNIEXPORT jdouble JNICALL Java_loci_formats_in_LegacyND2Reader_getDX
  (JNIEnv *, jobject, jint, jint, jint, jint);

/*
 * Class:     loci_formats_in_LegacyND2Reader
 * Method:    getDY
 * Signature: (IIII)D
 */
JNIEXPORT jdouble JNICALL Java_loci_formats_in_LegacyND2Reader_getDY
  (JNIEnv *, jobject, jint, jint, jint, jint);

/*
 * Class:     loci_formats_in_LegacyND2Reader
 * Method:    getDZ
 * Signature: (IIII)D
 */
JNIEXPORT jdouble JNICALL Java_loci_formats_in_LegacyND2Reader_getDZ
  (JNIEnv *, jobject, jint, jint, jint, jint);

/*
 * Class:     loci_formats_in_LegacyND2Reader
 * Method:    getDT
 * Signature: (IIII)D
 */
JNIEXPORT jdouble JNICALL Java_loci_formats_in_LegacyND2Reader_getDT
  (JNIEnv *, jobject, jint, jint, jint, jint);

/*
 * Class:     loci_formats_in_LegacyND2Reader
 * Method:    getWavelength
 * Signature: (IIII)D
 */
JNIEXPORT jdouble JNICALL Java_loci_formats_in_LegacyND2Reader_getWavelength
  (JNIEnv *, jobject, jint, jint, jint, jint);

/*
 * Class:     loci_formats_in_LegacyND2Reader
 * Method:    getChannelName
 * Signature: (IIII)Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL Java_loci_formats_in_LegacyND2Reader_getChannelName
  (JNIEnv *, jobject, jint, jint, jint, jint);

/*
 * Class:     loci_formats_in_LegacyND2Reader
 * Method:    getMagnification
 * Signature: (IIII)D
 */
JNIEXPORT jdouble JNICALL Java_loci_formats_in_LegacyND2Reader_getMagnification
  (JNIEnv *, jobject, jint, jint, jint, jint);

/*
 * Class:     loci_formats_in_LegacyND2Reader
 * Method:    getNA
 * Signature: (IIII)D
 */
JNIEXPORT jdouble JNICALL Java_loci_formats_in_LegacyND2Reader_getNA
  (JNIEnv *, jobject, jint, jint, jint, jint);

/*
 * Class:     loci_formats_in_LegacyND2Reader
 * Method:    getObjectiveName
 * Signature: (IIII)Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL Java_loci_formats_in_LegacyND2Reader_getObjectiveName
  (JNIEnv *, jobject, jint, jint, jint, jint);

/*
 * Class:     loci_formats_in_LegacyND2Reader
 * Method:    getModality
 * Signature: (IIII)I
 */
JNIEXPORT jint JNICALL Java_loci_formats_in_LegacyND2Reader_getModality
  (JNIEnv *, jobject, jint, jint, jint, jint);

unsigned long int getUID(int);

#ifdef __cplusplus
}
#endif
#endif
