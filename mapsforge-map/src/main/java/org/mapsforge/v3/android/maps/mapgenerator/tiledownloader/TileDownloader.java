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
package org.mapsforge.v3.android.maps.mapgenerator.tiledownloader;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.net.UnknownHostException;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.mapsforge.v3.android.maps.mapgenerator.MapGenerator;
import org.mapsforge.v3.android.maps.mapgenerator.MapGeneratorJob;
import org.mapsforge.v3.core.GeoPoint;
import org.mapsforge.v3.core.Tile;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

/**
 * Abstract base class for downloading map tiles from a server.
 */
public abstract class TileDownloader implements MapGenerator {
    private static final Logger LOG = Logger.getLogger(TileDownloader.class.getName());
    private static final GeoPoint START_POINT = new GeoPoint(51.33, 10.45);
    private static final Byte START_ZOOM_LEVEL = Byte.valueOf((byte) 5);

    private final int[] pixels;
    private String userAgent = null;

    /**
     * Default constructor that must be called by subclasses.
     */
    protected TileDownloader() {
        this.pixels = new int[Tile.TILE_SIZE * Tile.TILE_SIZE];
    }

    @Override
    public final void cleanup() {
        // do nothing
    }

	@Override
	public boolean executeJob(MapGeneratorJob mapGeneratorJob, Bitmap bitmap) {
		try {
			// Assuming mapGeneratorJob.tile contains the zoom level, x, and y coordinates
			Tile tile = mapGeneratorJob.tile;

			// Check if host, protocol, or port are null
			if (getHostName() == null || getProtocol() == null || getPort() == -1) {
				// If host, protocol, or port are null, use MBTiles method
				boolean success = getTileFromMBTiles(tile, bitmap);

				if (success) {
					return true;  // Success if tile was fetched and bitmap populated from MBTiles
				} else {
					// Log failure if the tile could not be fetched or decoded from MBTiles
					System.out.println("Failed to decode the bitmap for tile: " + getTilePath(tile));
				}
			} else {
				// If host, protocol, and port are not null, fetch the tile online
				boolean success = fetchTileOnline(tile, bitmap);

				if (success) {
					return true;  // Success if tile was fetched and bitmap populated online
				} else {
					// Log failure if the tile could not be fetched or decoded online
					System.out.println("Failed to fetch the tile online: " + getTilePath(tile));
				}
			}
		} catch (Exception e) {
			e.printStackTrace();  // Print any exceptions for debugging
		}

		return false;  // Return false if there was an error
	}

	// Method to fetch the tile online
	private boolean fetchTileOnline(Tile tile, Bitmap bitmap) {
		try {
			int port = getPort();
			// Use default ports if port is -1
			if (port == -1) {
				port = "https".equals(getProtocol()) ? 443 : 80;  // Default ports for HTTPS/HTTP
			}

			// Construct the URL for the tile
			URL url = new URL(getProtocol(), getHostName(), port, getTilePath(tile));
			URLConnection urlConnection = url.openConnection();

			// If User-Agent is specified, set it
			if (getUserAgent() != null) {
				urlConnection.setRequestProperty("User-Agent", getUserAgent());
			}

			// Get the InputStream from the URL
			InputStream inputStream = urlConnection.getInputStream();
			Bitmap decodedBitmap = BitmapFactory.decodeStream(inputStream);
			inputStream.close();

			// Check if the decoded bitmap is null
			if (decodedBitmap == null) {
				return false;  // Return false if decoding fails
			}

			// Copy pixels from the decoded bitmap to the provided bitmap
			decodedBitmap.getPixels(this.pixels, 0, Tile.TILE_SIZE, 0, 0, Tile.TILE_SIZE, Tile.TILE_SIZE);
			decodedBitmap.recycle();  // Recycle the decoded bitmap to free memory

			// Set pixels into the provided bitmap
			bitmap.setPixels(this.pixels, 0, Tile.TILE_SIZE, 0, 0, Tile.TILE_SIZE, Tile.TILE_SIZE);
			return true;  // Success
		} catch (UnknownHostException e) {
			LOG.log(Level.SEVERE, "Error: Unknown host", e);
			return false;
		} catch (IOException e) {
			LOG.log(Level.SEVERE, "Error: IO exception", e);
			return false;
		}
	}

	public boolean getTileFromMBTiles(Tile tile, Bitmap bitmap) {
		SQLiteDatabase database = null;

		// Hardcoded path to the MBTiles file
		String mbtilesFilePath = "/sdcard/.OSM/map.mbtiles";

		try {
			// Generate the tile path based on the tile's zoom, x, and y
			String tilePath = getTilePath(tile);  // Get the path "/zoomLevel/x/y.png"

			// Extract zoom, x, and y from the tile path (e.g., "/2/0/0.png" => zoom=2, x=0, y=0)
			String[] pathParts = tilePath.split("/");
			int zoom = Integer.parseInt(pathParts[1]);
			int x = Integer.parseInt(pathParts[2]);
			int y = Integer.parseInt(pathParts[3].replace(".png", ""));

			// Invert the Y-coordinate (common for tile storage formats)
			int invertedY = (int) (Math.pow(2, zoom) - 1) - y;

			// Open the MBTiles database from the specified file path
			database = SQLiteDatabase.openDatabase(mbtilesFilePath, null, SQLiteDatabase.OPEN_READONLY);

			// SQL query to get the tile data from the "tiles" table using the tile path
			String query = "SELECT tile_data FROM tiles WHERE zoom_level = ? AND tile_column = ? AND tile_row = ?";

			Cursor cursor = database.rawQuery(query, new String[]{String.valueOf(zoom), String.valueOf(x), String.valueOf(invertedY)});

			if (cursor != null && cursor.moveToFirst()) {
				try {
					// Get the byte array from the tile_data column
					byte[] tileData = cursor.getBlob(cursor.getColumnIndex("tile_data"));

					// Decode the byte array into a Bitmap
					Bitmap decodedBitmap = BitmapFactory.decodeByteArray(tileData, 0, tileData.length);
					if (decodedBitmap != null) {

						// Extract pixels from the decoded Bitmap
						int[] pixels = new int[Tile.TILE_SIZE * Tile.TILE_SIZE];
						decodedBitmap.getPixels(pixels, 0, Tile.TILE_SIZE, 0, 0, Tile.TILE_SIZE, Tile.TILE_SIZE);

						// Set the pixels into the provided Bitmap
						bitmap.setPixels(pixels, 0, Tile.TILE_SIZE, 0, 0, Tile.TILE_SIZE, Tile.TILE_SIZE);

						// Recycle the decoded bitmap to free up memory
						decodedBitmap.recycle();

						return true;  // Success
					} else {
						Log.e("MBTiles", "Failed to decode the bitmap from tile data.");
					}
				} catch (Exception e) {
					Log.e("MBTiles", "Error processing tile data", e);
				} finally {
					cursor.close();
				}
			} else {
			}

		} catch (Exception e) {
			return false;  // Return false if there's an issue opening the database or any other error
		} finally {
			// Close the database connection when done
			if (database != null && database.isOpen()) {
				database.close();
			}
		}

		return false;  // Return false if the tile was not found or there was an error
	}


    /**
     * @return the host name of the tile download server.
     */
    public abstract String getHostName();

    /**
     * @return the protocol which is used to connect to the server.
     */
    public abstract String getProtocol();

    /**
     * @return the port number of the tile download server. 
     * Default is -1, which indicates to use the default port for the protocol (80 for http and 443 for https).
     */
    public abstract int getPort();

    @Override
    public final GeoPoint getStartPoint() {
        return START_POINT;
    }

    @Override
    public final Byte getStartZoomLevel() {
        return START_ZOOM_LEVEL;
    }

    public void setUserAgent(final String userAgent) {
        this.userAgent = userAgent;
    }

    public String getUserAgent() {
        return this.userAgent;
    }

    /**
     * @param tile
     *            the tile for which a map image is required.
     * @return the absolute path to the map image.
     */
    public abstract String getTilePath(Tile tile);

    @Override
    public final boolean requiresInternetConnection() {
        return true;
    }

}
