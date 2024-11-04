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
	public final boolean executeJob(MapGeneratorJob mapGeneratorJob, Bitmap bitmap) {
		try {
			Tile tile = mapGeneratorJob.tile;
			int port = getPort();
			// Use the default ports if port is -1
			if (port == -1) {
				port = "https".equals(getProtocol()) ? 443 : 80; // Use default ports for HTTPS and HTTP
			}
			URL url = new URL(getProtocol(), getHostName(), port, getTilePath(tile));
			URLConnection urlConnection = url.openConnection();
			if (getUserAgent() != null) {
				urlConnection.setRequestProperty("User-Agent", getUserAgent());
			}
			InputStream inputStream = urlConnection.getInputStream();
			Bitmap decodedBitmap = BitmapFactory.decodeStream(inputStream);
			inputStream.close();

			// check if the input stream could be decoded into a bitmap
			if (decodedBitmap == null) {
				return false;
			}

			// copy all pixels from the decoded bitmap to the color array
			decodedBitmap.getPixels(this.pixels, 0, Tile.TILE_SIZE, 0, 0, Tile.TILE_SIZE, Tile.TILE_SIZE);
			decodedBitmap.recycle();

			// copy all pixels from the color array to the tile bitmap
			bitmap.setPixels(this.pixels, 0, Tile.TILE_SIZE, 0, 0, Tile.TILE_SIZE, Tile.TILE_SIZE);
			return true;
		} catch (UnknownHostException e) {
			LOG.log(Level.SEVERE, null, e);
			return false;
		} catch (IOException e) {
			LOG.log(Level.SEVERE, null, e);
			return false;
		}
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
