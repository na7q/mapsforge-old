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
package org.mapsforge.v3.android.maps.mapgenerator.databaserenderer;

import android.graphics.Paint;

class ShapePaintContainer {
	final Paint paint;
	final ShapeContainer shapeContainer;

	ShapePaintContainer(ShapeContainer shapeContainer, Paint paint) {
		this.shapeContainer = shapeContainer;
		this.paint = paint;
	}
}
