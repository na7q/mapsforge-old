/*
 * Copyright 2010, 2011, 2012 mapsforge.org
 *
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Lesser General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.mapsforge.v3.map.reader;

import java.io.File;

import org.junit.Assert;
import org.junit.Test;
import org.mapsforge.v3.core.BoundingBox;
import org.mapsforge.v3.core.GeoPoint;
import org.mapsforge.v3.map.reader.MapDatabase;
import org.mapsforge.v3.map.reader.header.FileOpenResult;
import org.mapsforge.v3.map.reader.header.MapFileInfo;

/**
 * Tests the {@link MapDatabase} class.
 */
public class MapDatabaseFileHeaderTest {
	private static final BoundingBox BOUNDING_BOX = new BoundingBox(100000, 200000, 300000, 400000);
	private static final String COMMENT = "testcomment";
	private static final String CREATED_BY = "mapsforge-map-writer-0.3.0-SNAPSHOT";
	private static final int FILE_SIZE = 709;
	private static final int FILE_VERSION = 3;
	private static final String LANGUAGE_PREFERENCE = "en";
	private static final long MAP_DATE = 1332081126944L;
	private static final File MAP_FILE = new File("src/test/resources/file_header/file_header.map");
	private static final int NUMBER_OF_SUBFILES = 3;
	private static final String PROJECTION_NAME = "Mercator";
	private static final GeoPoint START_POSITION = new GeoPoint(0.15, 0.25);
	private static final Byte START_ZOOM_LEVEL = Byte.valueOf((byte) 16);
	private static final int TILE_PIXEL_SIZE = 256;

	/**
	 * Tests the {@link MapDatabase#getMapFileInfo()} method.
	 */
	@Test
	public void getMapFileInfoTest() {
		MapDatabase mapDatabase = new MapDatabase();
		FileOpenResult fileOpenResult = mapDatabase.openFile(MAP_FILE);
		Assert.assertTrue(fileOpenResult.getErrorMessage(), fileOpenResult.isSuccess());

		MapFileInfo mapFileInfo = mapDatabase.getMapFileInfo();
		mapDatabase.closeFile();

		Assert.assertTrue(fileOpenResult.getErrorMessage(), fileOpenResult.isSuccess());
		Assert.assertNull(fileOpenResult.getErrorMessage());

		Assert.assertEquals(BOUNDING_BOX, mapFileInfo.boundingBox);
		Assert.assertEquals(FILE_SIZE, mapFileInfo.fileSize);
		Assert.assertEquals(FILE_VERSION, mapFileInfo.fileVersion);
		Assert.assertEquals(MAP_DATE, mapFileInfo.mapDate);
		Assert.assertEquals(NUMBER_OF_SUBFILES, mapFileInfo.numberOfSubFiles);
		Assert.assertEquals(PROJECTION_NAME, mapFileInfo.projectionName);
		Assert.assertEquals(TILE_PIXEL_SIZE, mapFileInfo.tilePixelSize);

		Assert.assertEquals(0, mapFileInfo.poiTags.length);
		Assert.assertEquals(0, mapFileInfo.wayTags.length);

		Assert.assertFalse(mapFileInfo.debugFile);
		Assert.assertEquals(START_POSITION, mapFileInfo.startPosition);
		Assert.assertEquals(START_ZOOM_LEVEL, mapFileInfo.startZoomLevel);
		Assert.assertEquals(LANGUAGE_PREFERENCE, mapFileInfo.languagePreference);
		Assert.assertEquals(COMMENT, mapFileInfo.comment);
		Assert.assertEquals(CREATED_BY, mapFileInfo.createdBy);
	}
}
