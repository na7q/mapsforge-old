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
package org.mapsforge.v3.android.maps.rendertheme;

import java.util.List;

import org.mapsforge.v3.core.Tag;

class NegativeRule extends Rule {
	final AttributeMatcher attributeMatcher;

	NegativeRule(ElementMatcher elementMatcher, ClosedMatcher closedMatcher, byte zoomMin, byte zoomMax,
			AttributeMatcher attributeMatcher) {
		super(elementMatcher, closedMatcher, zoomMin, zoomMax);

		this.attributeMatcher = attributeMatcher;
	}

	@Override
	boolean matchesNode(List<Tag> tags, byte zoomLevel) {
		return this.zoomMin <= zoomLevel && this.zoomMax >= zoomLevel && this.elementMatcher.matches(Element.NODE)
				&& this.attributeMatcher.matches(tags);
	}

	@Override
	boolean matchesWay(List<Tag> tags, byte zoomLevel, Closed closed) {
		return this.zoomMin <= zoomLevel && this.zoomMax >= zoomLevel && this.elementMatcher.matches(Element.WAY)
				&& this.closedMatcher.matches(closed) && this.attributeMatcher.matches(tags);
	}
}
