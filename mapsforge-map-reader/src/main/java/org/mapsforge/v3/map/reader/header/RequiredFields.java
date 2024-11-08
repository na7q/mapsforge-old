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
package org.mapsforge.v3.map.reader.header;

import java.io.IOException;

import org.mapsforge.v3.core.BoundingBox;
import org.mapsforge.v3.core.Tag;
import org.mapsforge.v3.core.Tile;
import org.mapsforge.v3.map.reader.ReadBuffer;

final class RequiredFields {
	/**
	 * Magic byte at the beginning of a valid binary map file.
	 */
	private static final String BINARY_OSM_MAGIC_BYTE = "mapsforge binary OSM";

	/**
	 * Maximum size of the file header in bytes.
	 */
	private static final int HEADER_SIZE_MAX = 1000000;

	/**
	 * Minimum size of the file header in bytes.
	 */
	private static final int HEADER_SIZE_MIN = 70;

	/**
	 * The name of the Mercator projection as stored in the file header.
	 */
	private static final String MERCATOR = "Mercator";

	/**
	 * A single whitespace character.
	 */
	private static final char SPACE = ' ';

	/**
	 * Version of the map file format which is supported by this implementation.
	 */
	private static final int SUPPORTED_FILE_VERSION = 3;

	/**
	 * The maximum latitude values in microdegrees.
	 */
	static final int LATITUDE_MAX = 90000000;

	/**
	 * The minimum latitude values in microdegrees.
	 */
	static final int LATITUDE_MIN = -90000000;

	/**
	 * The maximum longitude values in microdegrees.
	 */
	static final int LONGITUDE_MAX = 180000000;

	/**
	 * The minimum longitude values in microdegrees.
	 */
	static final int LONGITUDE_MIN = -180000000;

	static FileOpenResult readBoundingBox(ReadBuffer readBuffer, MapFileInfoBuilder mapFileInfoBuilder) {
		// get and check the minimum latitude (4 bytes)
		int minLatitude = readBuffer.readInt();
		if (minLatitude < LATITUDE_MIN || minLatitude > LATITUDE_MAX) {
			return new FileOpenResult("invalid minimum latitude: " + minLatitude);
		}

		// get and check the minimum longitude (4 bytes)
		int minLongitude = readBuffer.readInt();
		if (minLongitude < LONGITUDE_MIN || minLongitude > LONGITUDE_MAX) {
			return new FileOpenResult("invalid minimum longitude: " + minLongitude);
		}

		// get and check the maximum latitude (4 bytes)
		int maxLatitude = readBuffer.readInt();
		if (maxLatitude < LATITUDE_MIN || maxLatitude > LATITUDE_MAX) {
			return new FileOpenResult("invalid maximum latitude: " + maxLatitude);
		}

		// get and check the maximum longitude (4 bytes)
		int maxLongitude = readBuffer.readInt();
		if (maxLongitude < LONGITUDE_MIN || maxLongitude > LONGITUDE_MAX) {
			return new FileOpenResult("invalid maximum longitude: " + maxLongitude);
		}

		// check latitude and longitude range
		if (minLatitude > maxLatitude) {
			return new FileOpenResult("invalid latitude range: " + minLatitude + SPACE + maxLatitude);
		} else if (minLongitude > maxLongitude) {
			return new FileOpenResult("invalid longitude range: " + minLongitude + SPACE + maxLongitude);
		}

		mapFileInfoBuilder.boundingBox = new BoundingBox(minLatitude, minLongitude, maxLatitude, maxLongitude);
		return FileOpenResult.SUCCESS;
	}

	static FileOpenResult readFileSize(ReadBuffer readBuffer, long fileSize, MapFileInfoBuilder mapFileInfoBuilder) {
		// get and check the file size (8 bytes)
		long headerFileSize = readBuffer.readLong();
		if (headerFileSize != fileSize) {
			return new FileOpenResult("invalid file size: " + headerFileSize);
		}
		mapFileInfoBuilder.fileSize = fileSize;
		return FileOpenResult.SUCCESS;
	}

	static FileOpenResult readFileVersion(ReadBuffer readBuffer, MapFileInfoBuilder mapFileInfoBuilder) {
		// get and check the file version (4 bytes)
		int fileVersion = readBuffer.readInt();
		if (fileVersion != SUPPORTED_FILE_VERSION) {
			return new FileOpenResult("unsupported file version: " + fileVersion);
		}
		mapFileInfoBuilder.fileVersion = fileVersion;
		return FileOpenResult.SUCCESS;
	}

	static FileOpenResult readMagicByte(ReadBuffer readBuffer) throws IOException {
		// read the the magic byte and the file header size into the buffer
		int magicByteLength = BINARY_OSM_MAGIC_BYTE.length();
		if (!readBuffer.readFromFile(magicByteLength + 4)) {
			return new FileOpenResult("reading magic byte has failed");
		}

		// get and check the magic byte
		String magicByte = readBuffer.readUTF8EncodedString(magicByteLength);
		if (!BINARY_OSM_MAGIC_BYTE.equals(magicByte)) {
			return new FileOpenResult("invalid magic byte: " + magicByte);
		}
		return FileOpenResult.SUCCESS;
	}

	static FileOpenResult readMapDate(ReadBuffer readBuffer, MapFileInfoBuilder mapFileInfoBuilder) {
		// get and check the the map date (8 bytes)
		long mapDate = readBuffer.readLong();
		// is the map date before 2010-01-10 ?
		if (mapDate < 1200000000000L) {
			return new FileOpenResult("invalid map date: " + mapDate);
		}
		mapFileInfoBuilder.mapDate = mapDate;
		return FileOpenResult.SUCCESS;
	}

	static FileOpenResult readPoiTags(ReadBuffer readBuffer, MapFileInfoBuilder mapFileInfoBuilder) {
		// get and check the number of POI tags (2 bytes)
		int numberOfPoiTags = readBuffer.readShort();
		if (numberOfPoiTags < 0) {
			return new FileOpenResult("invalid number of POI tags: " + numberOfPoiTags);
		}

		Tag[] poiTags = new Tag[numberOfPoiTags];
		for (int currentTagId = 0; currentTagId < numberOfPoiTags; ++currentTagId) {
			// get and check the POI tag
			String tag = readBuffer.readUTF8EncodedString();
			if (tag == null) {
				return new FileOpenResult("POI tag must not be null: " + currentTagId);
			}
			poiTags[currentTagId] = new Tag(tag);
		}
		mapFileInfoBuilder.poiTags = poiTags;
		return FileOpenResult.SUCCESS;
	}

	static FileOpenResult readProjectionName(ReadBuffer readBuffer, MapFileInfoBuilder mapFileInfoBuilder) {
		// get and check the projection name
		String projectionName = readBuffer.readUTF8EncodedString();
		if (!MERCATOR.equals(projectionName)) {
			return new FileOpenResult("unsupported projection: " + projectionName);
		}
		mapFileInfoBuilder.projectionName = projectionName;
		return FileOpenResult.SUCCESS;
	}

	static FileOpenResult readRemainingHeader(ReadBuffer readBuffer) throws IOException {
		// get and check the size of the remaining file header (4 bytes)
		int remainingHeaderSize = readBuffer.readInt();
		if (remainingHeaderSize < HEADER_SIZE_MIN || remainingHeaderSize > HEADER_SIZE_MAX) {
			return new FileOpenResult("invalid remaining header size: " + remainingHeaderSize);
		}

		// read the header data into the buffer
		if (!readBuffer.readFromFile(remainingHeaderSize)) {
			return new FileOpenResult("reading header data has failed: " + remainingHeaderSize);
		}
		return FileOpenResult.SUCCESS;
	}

	static FileOpenResult readTilePixelSize(ReadBuffer readBuffer, MapFileInfoBuilder mapFileInfoBuilder) {
		// get and check the tile pixel size (2 bytes)
		int tilePixelSize = readBuffer.readShort();
		if (tilePixelSize != Tile.TILE_SIZE) {
			return new FileOpenResult("unsupported tile pixel size: " + tilePixelSize);
		}
		mapFileInfoBuilder.tilePixelSize = tilePixelSize;
		return FileOpenResult.SUCCESS;
	}

	static FileOpenResult readWayTags(ReadBuffer readBuffer, MapFileInfoBuilder mapFileInfoBuilder) {
		// get and check the number of way tags (2 bytes)
		int numberOfWayTags = readBuffer.readShort();
		if (numberOfWayTags < 0) {
			return new FileOpenResult("invalid number of way tags: " + numberOfWayTags);
		}

		Tag[] wayTags = new Tag[numberOfWayTags];

		for (int currentTagId = 0; currentTagId < numberOfWayTags; ++currentTagId) {
			// get and check the way tag
			String tag = readBuffer.readUTF8EncodedString();
			if (tag == null) {
				return new FileOpenResult("way tag must not be null: " + currentTagId);
			}
			wayTags[currentTagId] = new Tag(tag);
		}
		mapFileInfoBuilder.wayTags = wayTags;
		return FileOpenResult.SUCCESS;
	}

	private RequiredFields() {
		throw new IllegalStateException();
	}
}
