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

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.mapsforge.v3.core.Tile;

import android.graphics.Bitmap;
import android.graphics.Paint;
import android.graphics.Rect;

/**
 * This class process the methods for the Dependency Cache. It's connected with the LabelPlacement class. The main goal
 * is, to remove double labels and symbols that are already rendered, from the actual tile. Labels and symbols that,
 * would be rendered on an already drawn Tile, will be deleted too.
 */
class DependencyCache {
	/**
	 * The class holds the data for a symbol with dependencies on other tiles.
	 * 
	 * @param <Type>
	 *            only two types are reasonable. The DependencySymbol or DependencyText class.
	 */
	private static class Dependency<Type> {
		ImmutablePoint point;
		final Type value;

		Dependency(Type value, ImmutablePoint point) {
			this.value = value;
			this.point = point;
		}
	}

	/**
	 * This class holds all the information off the possible dependencies on a tile.
	 */
	private static class DependencyOnTile {
		boolean drawn;
		List<Dependency<DependencyText>> labels;
		List<Dependency<DependencySymbol>> symbols;

		/**
		 * Initialize label, symbol and drawn.
		 */
		DependencyOnTile() {
			this.labels = null;
			this.symbols = null;
			this.drawn = false;
		}

		/**
		 * @param toAdd
		 *            a dependency Symbol
		 */
		void addSymbol(Dependency<DependencySymbol> toAdd) {
			if (this.symbols == null) {
				this.symbols = new ArrayList<Dependency<DependencySymbol>>();
			}
			this.symbols.add(toAdd);
		}

		/**
		 * @param toAdd
		 *            a Dependency Text
		 */
		void addText(Dependency<DependencyText> toAdd) {
			if (this.labels == null) {
				this.labels = new ArrayList<Dependency<DependencyText>>();
			}
			this.labels.add(toAdd);
		}
	}

	/**
	 * The class holds the data for a symbol with dependencies on other tiles.
	 */
	private static class DependencySymbol {
		private final List<Tile> tiles;
		Bitmap symbol;

		/**
		 * Creates a symbol dependency element for the dependency cache.
		 * 
		 * @param symbol
		 *            reference on the dependency symbol.
		 * @param tile
		 *            dependency tile.
		 */
		DependencySymbol(Bitmap symbol, Tile tile) {
			this.symbol = symbol;
			this.tiles = new LinkedList<Tile>();
			this.tiles.add(tile);
		}

		/**
		 * Adds an additional tile, which has an dependency with this symbol.
		 * 
		 * @param tile
		 *            additional tile.
		 */
		void addTile(Tile tile) {
			this.tiles.add(tile);
		}
	}

	/**
	 * The class holds the data for a label with dependencies on other tiles.
	 */
	private static class DependencyText {
		final Rect boundary;
		final Paint paintBack;
		final Paint paintFront;
		final String text;
		List<Tile> tiles;

		/**
		 * Creates a text dependency in the dependency cache.
		 * 
		 * @param paintFront
		 *            paint element from the front.
		 * @param paintBack
		 *            paint element form the background of the text.
		 * @param text
		 *            the text of the element.
		 * @param boundary
		 *            the fixed boundary with width and height.
		 * @param tile
		 *            all tile in where the element has an influence.
		 */
		DependencyText(Paint paintFront, Paint paintBack, String text, Rect boundary, Tile tile) {
			this.paintFront = paintFront;
			this.paintBack = paintBack;
			this.text = text;
			this.tiles = new LinkedList<Tile>();
			this.tiles.add(tile);
			this.boundary = boundary;
		}

		void addTile(Tile tile) {
			this.tiles.add(tile);
		}
	}

	private DependencyOnTile currentDependencyOnTile;
	private Tile currentTile;

	/**
	 * Hash table, that connects the Tiles with their entries in the dependency cache.
	 */
	final Map<Tile, DependencyOnTile> dependencyTable;
	Dependency<DependencyText> depLabel;
	Rect rect1;
	Rect rect2;
	SymbolContainer smb;
	DependencyOnTile tmp;

	/**
	 * Constructor for this class, that creates a hashtable for the dependencies.
	 */
	DependencyCache() {
		this.dependencyTable = new Hashtable<Tile, DependencyOnTile>(60);
	}

	private void addLabelsFromDependencyOnTile(List<PointTextContainer> labels) {
		for (int i = 0; i < this.currentDependencyOnTile.labels.size(); i++) {
			this.depLabel = this.currentDependencyOnTile.labels.get(i);
			if (this.depLabel.value.paintBack != null) {
				labels.add(new PointTextContainer(this.depLabel.value.text, this.depLabel.point.pointX,
						this.depLabel.point.pointY, this.depLabel.value.paintFront, this.depLabel.value.paintBack));
			} else {
				labels.add(new PointTextContainer(this.depLabel.value.text, this.depLabel.point.pointX,
						this.depLabel.point.pointY, this.depLabel.value.paintFront));
			}
		}
	}

	private void addSymbolsFromDependencyOnTile(List<SymbolContainer> symbols) {
		for (Dependency<DependencySymbol> depSmb : this.currentDependencyOnTile.symbols) {
			symbols.add(new SymbolContainer(depSmb.value.symbol, depSmb.point.pointX, depSmb.point.pointY));
		}
	}

	/**
	 * Fills the dependency entry from the tile and the neighbor tiles with the dependency information, that are
	 * necessary for drawing. To do that every label and symbol that will be drawn, will be checked if it produces
	 * dependencies with other tiles.
	 * 
	 * @param pTC
	 *            list of the labels
	 */
	private void fillDependencyLabels(List<PointTextContainer> pTC) {
		Tile left = new Tile(this.currentTile.tileX - 1, this.currentTile.tileY, this.currentTile.zoomLevel);
		Tile right = new Tile(this.currentTile.tileX + 1, this.currentTile.tileY, this.currentTile.zoomLevel);
		Tile up = new Tile(this.currentTile.tileX, this.currentTile.tileY - 1, this.currentTile.zoomLevel);
		Tile down = new Tile(this.currentTile.tileX, this.currentTile.tileY + 1, this.currentTile.zoomLevel);

		Tile leftup = new Tile(this.currentTile.tileX - 1, this.currentTile.tileY - 1, this.currentTile.zoomLevel);
		Tile leftdown = new Tile(this.currentTile.tileX - 1, this.currentTile.tileY + 1, this.currentTile.zoomLevel);
		Tile rightup = new Tile(this.currentTile.tileX + 1, this.currentTile.tileY - 1, this.currentTile.zoomLevel);
		Tile rightdown = new Tile(this.currentTile.tileX + 1, this.currentTile.tileY + 1, this.currentTile.zoomLevel);

		PointTextContainer label;
		DependencyOnTile linkedDep;
		DependencyText toAdd;

		for (int i = 0; i < pTC.size(); i++) {

			label = pTC.get(i);

			toAdd = null;

			// up
			if ((label.y - label.boundary.height() < 0.0f) && (!this.dependencyTable.get(up).drawn)) {
				linkedDep = this.dependencyTable.get(up);

				toAdd = new DependencyText(label.paintFront, label.paintBack, label.text, label.boundary,
						this.currentTile);

				this.currentDependencyOnTile.addText(new Dependency<DependencyText>(toAdd, new ImmutablePoint(label.x,
						label.y)));

				linkedDep.addText(new Dependency<DependencyText>(toAdd, new ImmutablePoint(label.x, label.y
						+ Tile.TILE_SIZE)));

				toAdd.addTile(up);

				if ((label.x < 0.0f) && (!this.dependencyTable.get(leftup).drawn)) {
					linkedDep = this.dependencyTable.get(leftup);

					linkedDep.addText(new Dependency<DependencyText>(toAdd, new ImmutablePoint(
							label.x + Tile.TILE_SIZE, label.y + Tile.TILE_SIZE)));

					toAdd.addTile(leftup);
				}

				if ((label.x + label.boundary.width() > Tile.TILE_SIZE) && (!this.dependencyTable.get(rightup).drawn)) {
					linkedDep = this.dependencyTable.get(rightup);

					linkedDep.addText(new Dependency<DependencyText>(toAdd, new ImmutablePoint(
							label.x - Tile.TILE_SIZE, label.y + Tile.TILE_SIZE)));

					toAdd.addTile(rightup);
				}
			}

			// down
			if ((label.y > Tile.TILE_SIZE) && (!this.dependencyTable.get(down).drawn)) {

				linkedDep = this.dependencyTable.get(down);

				if (toAdd == null) {
					toAdd = new DependencyText(label.paintFront, label.paintBack, label.text, label.boundary,
							this.currentTile);

					this.currentDependencyOnTile.addText(new Dependency<DependencyText>(toAdd, new ImmutablePoint(
							label.x, label.y)));

				}

				linkedDep.addText(new Dependency<DependencyText>(toAdd, new ImmutablePoint(label.x, label.y
						- Tile.TILE_SIZE)));

				toAdd.addTile(down);

				if ((label.x < 0.0f) && (!this.dependencyTable.get(leftdown).drawn)) {
					linkedDep = this.dependencyTable.get(leftdown);

					linkedDep.addText(new Dependency<DependencyText>(toAdd, new ImmutablePoint(
							label.x + Tile.TILE_SIZE, label.y - Tile.TILE_SIZE)));

					toAdd.addTile(leftdown);
				}

				if ((label.x + label.boundary.width() > Tile.TILE_SIZE) && (!this.dependencyTable.get(rightdown).drawn)) {

					linkedDep = this.dependencyTable.get(rightdown);

					linkedDep.addText(new Dependency<DependencyText>(toAdd, new ImmutablePoint(
							label.x - Tile.TILE_SIZE, label.y - Tile.TILE_SIZE)));

					toAdd.addTile(rightdown);
				}
			}
			// left

			if ((label.x < 0.0f) && (!this.dependencyTable.get(left).drawn)) {
				linkedDep = this.dependencyTable.get(left);

				if (toAdd == null) {
					toAdd = new DependencyText(label.paintFront, label.paintBack, label.text, label.boundary,
							this.currentTile);

					this.currentDependencyOnTile.addText(new Dependency<DependencyText>(toAdd, new ImmutablePoint(
							label.x, label.y)));
				}

				linkedDep.addText(new Dependency<DependencyText>(toAdd, new ImmutablePoint(label.x + Tile.TILE_SIZE,
						label.y)));

				toAdd.addTile(left);
			}
			// right
			if ((label.x + label.boundary.width() > Tile.TILE_SIZE) && (!this.dependencyTable.get(right).drawn)) {
				linkedDep = this.dependencyTable.get(right);

				if (toAdd == null) {
					toAdd = new DependencyText(label.paintFront, label.paintBack, label.text, label.boundary,
							this.currentTile);

					this.currentDependencyOnTile.addText(new Dependency<DependencyText>(toAdd, new ImmutablePoint(
							label.x, label.y)));
				}

				linkedDep.addText(new Dependency<DependencyText>(toAdd, new ImmutablePoint(label.x - Tile.TILE_SIZE,
						label.y)));

				toAdd.addTile(right);
			}

			// check symbols

			if ((label.symbol != null) && (toAdd == null)) {

				if ((label.symbol.y <= 0.0f) && (!this.dependencyTable.get(up).drawn)) {
					linkedDep = this.dependencyTable.get(up);

					toAdd = new DependencyText(label.paintFront, label.paintBack, label.text, label.boundary,
							this.currentTile);

					this.currentDependencyOnTile.addText(new Dependency<DependencyText>(toAdd, new ImmutablePoint(
							label.x, label.y)));

					linkedDep.addText(new Dependency<DependencyText>(toAdd, new ImmutablePoint(label.x, label.y
							+ Tile.TILE_SIZE)));

					toAdd.addTile(up);

					if ((label.symbol.x < 0.0f) && (!this.dependencyTable.get(leftup).drawn)) {
						linkedDep = this.dependencyTable.get(leftup);

						linkedDep.addText(new Dependency<DependencyText>(toAdd, new ImmutablePoint(label.x
								+ Tile.TILE_SIZE, label.y + Tile.TILE_SIZE)));

						toAdd.addTile(leftup);
					}

					if ((label.symbol.x + label.symbol.symbol.getWidth() > Tile.TILE_SIZE)
							&& (!this.dependencyTable.get(rightup).drawn)) {
						linkedDep = this.dependencyTable.get(rightup);

						linkedDep.addText(new Dependency<DependencyText>(toAdd, new ImmutablePoint(label.x
								- Tile.TILE_SIZE, label.y + Tile.TILE_SIZE)));

						toAdd.addTile(rightup);
					}
				}

				if ((label.symbol.y + label.symbol.symbol.getHeight() >= Tile.TILE_SIZE)
						&& (!this.dependencyTable.get(down).drawn)) {

					linkedDep = this.dependencyTable.get(down);

					if (toAdd == null) {
						toAdd = new DependencyText(label.paintFront, label.paintBack, label.text, label.boundary,
								this.currentTile);

						this.currentDependencyOnTile.addText(new Dependency<DependencyText>(toAdd, new ImmutablePoint(
								label.x, label.y)));
					}

					linkedDep.addText(new Dependency<DependencyText>(toAdd, new ImmutablePoint(label.x, label.y
							+ Tile.TILE_SIZE)));

					toAdd.addTile(up);

					if ((label.symbol.x < 0.0f) && (!this.dependencyTable.get(leftdown).drawn)) {
						linkedDep = this.dependencyTable.get(leftdown);

						linkedDep.addText(new Dependency<DependencyText>(toAdd, new ImmutablePoint(label.x
								+ Tile.TILE_SIZE, label.y - Tile.TILE_SIZE)));

						toAdd.addTile(leftdown);
					}

					if ((label.symbol.x + label.symbol.symbol.getWidth() > Tile.TILE_SIZE)
							&& (!this.dependencyTable.get(rightdown).drawn)) {

						linkedDep = this.dependencyTable.get(rightdown);

						linkedDep.addText(new Dependency<DependencyText>(toAdd, new ImmutablePoint(label.x
								- Tile.TILE_SIZE, label.y - Tile.TILE_SIZE)));

						toAdd.addTile(rightdown);
					}
				}

				if ((label.symbol.x <= 0.0f) && (!this.dependencyTable.get(left).drawn)) {
					linkedDep = this.dependencyTable.get(left);

					if (toAdd == null) {
						toAdd = new DependencyText(label.paintFront, label.paintBack, label.text, label.boundary,
								this.currentTile);

						this.currentDependencyOnTile.addText(new Dependency<DependencyText>(toAdd, new ImmutablePoint(
								label.x, label.y)));
					}

					linkedDep.addText(new Dependency<DependencyText>(toAdd, new ImmutablePoint(
							label.x - Tile.TILE_SIZE, label.y)));

					toAdd.addTile(left);
				}

				if ((label.symbol.x + label.symbol.symbol.getWidth() >= Tile.TILE_SIZE)
						&& (!this.dependencyTable.get(right).drawn)) {
					linkedDep = this.dependencyTable.get(right);

					if (toAdd == null) {
						toAdd = new DependencyText(label.paintFront, label.paintBack, label.text, label.boundary,
								this.currentTile);

						this.currentDependencyOnTile.addText(new Dependency<DependencyText>(toAdd, new ImmutablePoint(
								label.x, label.y)));
					}

					linkedDep.addText(new Dependency<DependencyText>(toAdd, new ImmutablePoint(
							label.x + Tile.TILE_SIZE, label.y)));

					toAdd.addTile(right);
				}
			}
		}
	}

	private void fillDependencyOnTile2(List<PointTextContainer> labels, List<SymbolContainer> symbols,
			List<PointTextContainer> areaLabels) {
		Tile left = new Tile(this.currentTile.tileX - 1, this.currentTile.tileY, this.currentTile.zoomLevel);
		Tile right = new Tile(this.currentTile.tileX + 1, this.currentTile.tileY, this.currentTile.zoomLevel);
		Tile up = new Tile(this.currentTile.tileX, this.currentTile.tileY - 1, this.currentTile.zoomLevel);
		Tile down = new Tile(this.currentTile.tileX, this.currentTile.tileY + 1, this.currentTile.zoomLevel);

		Tile leftup = new Tile(this.currentTile.tileX - 1, this.currentTile.tileY - 1, this.currentTile.zoomLevel);
		Tile leftdown = new Tile(this.currentTile.tileX - 1, this.currentTile.tileY + 1, this.currentTile.zoomLevel);
		Tile rightup = new Tile(this.currentTile.tileX + 1, this.currentTile.tileY - 1, this.currentTile.zoomLevel);
		Tile rightdown = new Tile(this.currentTile.tileX + 1, this.currentTile.tileY + 1, this.currentTile.zoomLevel);

		if (this.dependencyTable.get(up) == null) {
			this.dependencyTable.put(up, new DependencyOnTile());
		}
		if (this.dependencyTable.get(down) == null) {
			this.dependencyTable.put(down, new DependencyOnTile());
		}
		if (this.dependencyTable.get(left) == null) {
			this.dependencyTable.put(left, new DependencyOnTile());
		}
		if (this.dependencyTable.get(right) == null) {
			this.dependencyTable.put(right, new DependencyOnTile());
		}
		if (this.dependencyTable.get(leftdown) == null) {
			this.dependencyTable.put(leftdown, new DependencyOnTile());
		}
		if (this.dependencyTable.get(rightup) == null) {
			this.dependencyTable.put(rightup, new DependencyOnTile());
		}
		if (this.dependencyTable.get(leftup) == null) {
			this.dependencyTable.put(leftup, new DependencyOnTile());
		}
		if (this.dependencyTable.get(rightdown) == null) {
			this.dependencyTable.put(rightdown, new DependencyOnTile());
		}

		fillDependencyLabels(labels);
		fillDependencyLabels(areaLabels);

		DependencyOnTile linkedDep;
		DependencySymbol addSmb;

		for (SymbolContainer symbol : symbols) {
			addSmb = null;

			// up
			if ((symbol.y < 0.0f) && (!this.dependencyTable.get(up).drawn)) {
				linkedDep = this.dependencyTable.get(up);

				addSmb = new DependencySymbol(symbol.symbol, this.currentTile);
				this.currentDependencyOnTile.addSymbol(new Dependency<DependencySymbol>(addSmb, new ImmutablePoint(
						symbol.x, symbol.y)));

				linkedDep.addSymbol(new Dependency<DependencySymbol>(addSmb, new ImmutablePoint(symbol.x, symbol.y
						+ Tile.TILE_SIZE)));
				addSmb.addTile(up);

				if ((symbol.x < 0.0f) && (!this.dependencyTable.get(leftup).drawn)) {
					linkedDep = this.dependencyTable.get(leftup);

					linkedDep.addSymbol(new Dependency<DependencySymbol>(addSmb, new ImmutablePoint(symbol.x
							+ Tile.TILE_SIZE, symbol.y + Tile.TILE_SIZE)));
					addSmb.addTile(leftup);
				}

				if ((symbol.x + symbol.symbol.getWidth() > Tile.TILE_SIZE)
						&& (!this.dependencyTable.get(rightup).drawn)) {
					linkedDep = this.dependencyTable.get(rightup);

					linkedDep.addSymbol(new Dependency<DependencySymbol>(addSmb, new ImmutablePoint(symbol.x
							- Tile.TILE_SIZE, symbol.y + Tile.TILE_SIZE)));
					addSmb.addTile(rightup);
				}
			}

			// down
			if ((symbol.y + symbol.symbol.getHeight() > Tile.TILE_SIZE) && (!this.dependencyTable.get(down).drawn)) {

				linkedDep = this.dependencyTable.get(down);

				if (addSmb == null) {
					addSmb = new DependencySymbol(symbol.symbol, this.currentTile);
					this.currentDependencyOnTile.addSymbol(new Dependency<DependencySymbol>(addSmb, new ImmutablePoint(
							symbol.x, symbol.y)));
				}

				linkedDep.addSymbol(new Dependency<DependencySymbol>(addSmb, new ImmutablePoint(symbol.x, symbol.y
						- Tile.TILE_SIZE)));
				addSmb.addTile(down);

				if ((symbol.x < 0.0f) && (!this.dependencyTable.get(leftdown).drawn)) {
					linkedDep = this.dependencyTable.get(leftdown);

					linkedDep.addSymbol(new Dependency<DependencySymbol>(addSmb, new ImmutablePoint(symbol.x
							+ Tile.TILE_SIZE, symbol.y - Tile.TILE_SIZE)));
					addSmb.addTile(leftdown);
				}

				if ((symbol.x + symbol.symbol.getWidth() > Tile.TILE_SIZE)
						&& (!this.dependencyTable.get(rightdown).drawn)) {

					linkedDep = this.dependencyTable.get(rightdown);

					linkedDep.addSymbol(new Dependency<DependencySymbol>(addSmb, new ImmutablePoint(symbol.x
							- Tile.TILE_SIZE, symbol.y - Tile.TILE_SIZE)));
					addSmb.addTile(rightdown);
				}
			}

			// left
			if ((symbol.x < 0.0f) && (!this.dependencyTable.get(left).drawn)) {
				linkedDep = this.dependencyTable.get(left);

				if (addSmb == null) {
					addSmb = new DependencySymbol(symbol.symbol, this.currentTile);
					this.currentDependencyOnTile.addSymbol(new Dependency<DependencySymbol>(addSmb, new ImmutablePoint(
							symbol.x, symbol.y)));
				}

				linkedDep.addSymbol(new Dependency<DependencySymbol>(addSmb, new ImmutablePoint(symbol.x
						+ Tile.TILE_SIZE, symbol.y)));
				addSmb.addTile(left);
			}

			// right
			if ((symbol.x + symbol.symbol.getWidth() > Tile.TILE_SIZE) && (!this.dependencyTable.get(right).drawn)) {
				linkedDep = this.dependencyTable.get(right);
				if (addSmb == null) {
					addSmb = new DependencySymbol(symbol.symbol, this.currentTile);
					this.currentDependencyOnTile.addSymbol(new Dependency<DependencySymbol>(addSmb, new ImmutablePoint(
							symbol.x, symbol.y)));
				}

				linkedDep.addSymbol(new Dependency<DependencySymbol>(addSmb, new ImmutablePoint(symbol.x
						- Tile.TILE_SIZE, symbol.y)));
				addSmb.addTile(right);
			}
		}
	}

	private void removeOverlappingAreaLabelsWithDependencyLabels(List<PointTextContainer> areaLabels) {
		PointTextContainer pTC;

		for (int i = 0; i < this.currentDependencyOnTile.labels.size(); i++) {
			this.depLabel = this.currentDependencyOnTile.labels.get(i);
			this.rect1 = new android.graphics.Rect((int) (this.depLabel.point.pointX),
					(int) (this.depLabel.point.pointY - this.depLabel.value.boundary.height()),
					(int) (this.depLabel.point.pointX + this.depLabel.value.boundary.width()),
					(int) (this.depLabel.point.pointY));

			for (int x = 0; x < areaLabels.size(); x++) {
				pTC = areaLabels.get(x);

				this.rect2 = new android.graphics.Rect((int) pTC.x, (int) pTC.y - pTC.boundary.height(), (int) pTC.x
						+ pTC.boundary.width(), (int) pTC.y);

				if (android.graphics.Rect.intersects(this.rect2, this.rect1)) {
					areaLabels.remove(x);
					x--;
				}
			}
		}
	}

	private void removeOverlappingAreaLabelsWithDependencySymbols(List<PointTextContainer> areaLabels) {
		PointTextContainer label;

		for (Dependency<DependencySymbol> depSmb : this.currentDependencyOnTile.symbols) {

			this.rect1 = new android.graphics.Rect((int) depSmb.point.pointX, (int) depSmb.point.pointY,
					(int) depSmb.point.pointX + depSmb.value.symbol.getWidth(), (int) depSmb.point.pointY
							+ depSmb.value.symbol.getHeight());

			for (int x = 0; x < areaLabels.size(); x++) {
				label = areaLabels.get(x);

				this.rect2 = new android.graphics.Rect((int) (label.x), (int) (label.y - label.boundary.height()),
						(int) (label.x + label.boundary.width()), (int) (label.y));

				if (android.graphics.Rect.intersects(this.rect2, this.rect1)) {
					areaLabels.remove(x);
					x--;
				}
			}
		}
	}

	private void removeOverlappingLabelsWithDependencyLabels(List<PointTextContainer> labels) {
		for (int i = 0; i < this.currentDependencyOnTile.labels.size(); i++) {
			for (int x = 0; x < labels.size(); x++) {
				if ((labels.get(x).text.equals(this.currentDependencyOnTile.labels.get(i).value.text))
						&& (labels.get(x).paintFront
								.equals(this.currentDependencyOnTile.labels.get(i).value.paintFront))
						&& (labels.get(x).paintBack.equals(this.currentDependencyOnTile.labels.get(i).value.paintBack))) {
					labels.remove(x);
					i--;
					break;
				}
			}
		}
	}

	private void removeOverlappingSymbolsWithDepencySymbols(List<SymbolContainer> symbols, int dis) {
		SymbolContainer sym;
		Dependency<DependencySymbol> sym2;

		for (int x = 0; x < this.currentDependencyOnTile.symbols.size(); x++) {
			sym2 = this.currentDependencyOnTile.symbols.get(x);
			this.rect1 = new android.graphics.Rect((int) sym2.point.pointX - dis, (int) sym2.point.pointY - dis,
					(int) sym2.point.pointX + sym2.value.symbol.getWidth() + dis, (int) sym2.point.pointY
							+ sym2.value.symbol.getHeight() + dis);

			for (int y = 0; y < symbols.size(); y++) {

				sym = symbols.get(y);
				this.rect2 = new android.graphics.Rect((int) sym.x, (int) sym.y, (int) sym.x + sym.symbol.getWidth(),
						(int) sym.y + sym.symbol.getHeight());

				if (android.graphics.Rect.intersects(this.rect2, this.rect1)) {
					symbols.remove(y);
					y--;
				}
			}
		}
	}

	private void removeOverlappingSymbolsWithDependencyLabels(List<SymbolContainer> symbols) {
		for (int i = 0; i < this.currentDependencyOnTile.labels.size(); i++) {
			this.depLabel = this.currentDependencyOnTile.labels.get(i);
			this.rect1 = new android.graphics.Rect((int) (this.depLabel.point.pointX),
					(int) (this.depLabel.point.pointY - this.depLabel.value.boundary.height()),
					(int) (this.depLabel.point.pointX + this.depLabel.value.boundary.width()),
					(int) (this.depLabel.point.pointY));

			for (int x = 0; x < symbols.size(); x++) {
				this.smb = symbols.get(x);

				this.rect2 = new android.graphics.Rect((int) this.smb.x, (int) this.smb.y, (int) this.smb.x
						+ this.smb.symbol.getWidth(), (int) this.smb.y + this.smb.symbol.getHeight());

				if (android.graphics.Rect.intersects(this.rect2, this.rect1)) {
					symbols.remove(x);
					x--;
				}
			}
		}
	}

	/**
	 * This method fills the entries in the dependency cache of the tiles, if their dependencies.
	 * 
	 * @param labels
	 *            current labels, that will be displayed.
	 * @param symbols
	 *            current symbols, that will be displayed.
	 * @param areaLabels
	 *            current areaLabels, that will be displayed.
	 */
	void fillDependencyOnTile(List<PointTextContainer> labels, List<SymbolContainer> symbols,
			List<PointTextContainer> areaLabels) {
		this.currentDependencyOnTile.drawn = true;

		if ((!labels.isEmpty()) || (!symbols.isEmpty()) || (!areaLabels.isEmpty())) {
			fillDependencyOnTile2(labels, symbols, areaLabels);
		}

		if (this.currentDependencyOnTile.labels != null) {
			addLabelsFromDependencyOnTile(labels);
		}
		if (this.currentDependencyOnTile.symbols != null) {
			addSymbolsFromDependencyOnTile(symbols);
		}
	}

	/**
	 * This method must be called, before the dependencies will be handled correctly. Because it sets the actual Tile
	 * and looks if it has already dependencies.
	 * 
	 * @param tile
	 *            the current Tile
	 */
	void generateTileAndDependencyOnTile(Tile tile) {
		this.currentTile = new Tile(tile.tileX, tile.tileY, tile.zoomLevel);
		this.currentDependencyOnTile = this.dependencyTable.get(this.currentTile);

		if (this.currentDependencyOnTile == null) {
			this.dependencyTable.put(this.currentTile, new DependencyOnTile());
			this.currentDependencyOnTile = this.dependencyTable.get(this.currentTile);
		}
	}

	/**
	 * Removes the are labels from the actual list, that would be rendered in a Tile that has already be drawn.
	 * 
	 * @param areaLabels
	 *            current area Labels, that will be displayed
	 */
	void removeAreaLabelsInAlreadyDrawnAreas(List<PointTextContainer> areaLabels) {
		Tile lefttmp = new Tile(this.currentTile.tileX - 1, this.currentTile.tileY, this.currentTile.zoomLevel);
		Tile righttmp = new Tile(this.currentTile.tileX + 1, this.currentTile.tileY, this.currentTile.zoomLevel);
		Tile uptmp = new Tile(this.currentTile.tileX, this.currentTile.tileY - 1, this.currentTile.zoomLevel);
		Tile downtmp = new Tile(this.currentTile.tileX, this.currentTile.tileY + 1, this.currentTile.zoomLevel);

		boolean up;
		boolean left;
		boolean right;
		boolean down;

		this.tmp = this.dependencyTable.get(lefttmp);
		left = this.tmp == null ? false : this.tmp.drawn;

		this.tmp = this.dependencyTable.get(righttmp);
		right = this.tmp == null ? false : this.tmp.drawn;

		this.tmp = this.dependencyTable.get(uptmp);
		up = this.tmp == null ? false : this.tmp.drawn;

		this.tmp = this.dependencyTable.get(downtmp);
		down = this.tmp == null ? false : this.tmp.drawn;

		PointTextContainer label;

		for (int i = 0; i < areaLabels.size(); i++) {
			label = areaLabels.get(i);

			if (up && label.y - label.boundary.height() < 0.0f) {
				areaLabels.remove(i);
				i--;
				continue;
			}

			if (down && label.y > Tile.TILE_SIZE) {
				areaLabels.remove(i);
				i--;
				continue;
			}
			if (left && label.x < 0.0f) {
				areaLabels.remove(i);
				i--;
				continue;
			}
			if (right && label.x + label.boundary.width() > Tile.TILE_SIZE) {
				areaLabels.remove(i);
				i--;
				continue;
			}
		}
	}

	/**
	 * Removes all objects that overlaps with the objects from the dependency cache.
	 * 
	 * @param labels
	 *            labels from the current tile
	 * @param areaLabels
	 *            area labels from the current tile
	 * @param symbols
	 *            symbols from the current tile
	 */
	void removeOverlappingObjectsWithDependencyOnTile(List<PointTextContainer> labels,
			List<PointTextContainer> areaLabels, List<SymbolContainer> symbols) {
		if (this.currentDependencyOnTile.labels != null && this.currentDependencyOnTile.labels.size() != 0) {
			removeOverlappingLabelsWithDependencyLabels(labels);
			removeOverlappingSymbolsWithDependencyLabels(symbols);
			removeOverlappingAreaLabelsWithDependencyLabels(areaLabels);
		}

		if (this.currentDependencyOnTile.symbols != null && this.currentDependencyOnTile.symbols.size() != 0) {
			removeOverlappingSymbolsWithDepencySymbols(symbols, 2);
			removeOverlappingAreaLabelsWithDependencySymbols(areaLabels);
		}
	}

	/**
	 * When the LabelPlacement class generates potential label positions for an POI, there should be no possible
	 * positions, that collide with existing symbols or labels in the dependency Cache. This class implements this
	 * functionality.
	 * 
	 * @param refPos
	 *            possible label positions form the two or four point Greedy
	 */
	void removeReferencePointsFromDependencyCache(LabelPlacement.ReferencePosition[] refPos) {
		Tile lefttmp = new Tile(this.currentTile.tileX - 1, this.currentTile.tileY, this.currentTile.zoomLevel);
		Tile righttmp = new Tile(this.currentTile.tileX + 1, this.currentTile.tileY, this.currentTile.zoomLevel);
		Tile uptmp = new Tile(this.currentTile.tileX, this.currentTile.tileY - 1, this.currentTile.zoomLevel);
		Tile downtmp = new Tile(this.currentTile.tileX, this.currentTile.tileY + 1, this.currentTile.zoomLevel);

		boolean up;
		boolean left;
		boolean right;
		boolean down;

		this.tmp = this.dependencyTable.get(lefttmp);
		left = this.tmp == null ? false : this.tmp.drawn;

		this.tmp = this.dependencyTable.get(righttmp);
		right = this.tmp == null ? false : this.tmp.drawn;

		this.tmp = this.dependencyTable.get(uptmp);
		up = this.tmp == null ? false : this.tmp.drawn;

		this.tmp = this.dependencyTable.get(downtmp);
		down = this.tmp == null ? false : this.tmp.drawn;

		LabelPlacement.ReferencePosition ref;

		for (int i = 0; i < refPos.length; i++) {
			ref = refPos[i];

			if (ref == null) {
				continue;
			}

			if (up && ref.y - ref.height < 0) {
				refPos[i] = null;
				continue;
			}

			if (down && ref.y >= Tile.TILE_SIZE) {
				refPos[i] = null;
				continue;
			}

			if (left && ref.x < 0) {
				refPos[i] = null;
				continue;
			}

			if (right && ref.x + ref.width > Tile.TILE_SIZE) {
				refPos[i] = null;
			}
		}

		// removes all Reverence Points that intersects with Labels from the Dependency Cache

		int dis = 2;
		if (this.currentDependencyOnTile != null) {
			if (this.currentDependencyOnTile.labels != null) {
				for (int i = 0; i < this.currentDependencyOnTile.labels.size(); i++) {
					this.depLabel = this.currentDependencyOnTile.labels.get(i);
					this.rect1 = new android.graphics.Rect((int) this.depLabel.point.pointX - dis,
							(int) (this.depLabel.point.pointY - this.depLabel.value.boundary.height()) - dis,
							(int) (this.depLabel.point.pointX + this.depLabel.value.boundary.width() + dis),
							(int) (this.depLabel.point.pointY + dis));

					for (int y = 0; y < refPos.length; y++) {
						if (refPos[y] != null) {
							this.rect2 = new android.graphics.Rect((int) refPos[y].x,
									(int) (refPos[y].y - refPos[y].height), (int) (refPos[y].x + refPos[y].width),
									(int) (refPos[y].y));

							if (android.graphics.Rect.intersects(this.rect2, this.rect1)) {
								refPos[y] = null;
							}
						}
					}
				}
			}
			if (this.currentDependencyOnTile.symbols != null) {
				for (Dependency<DependencySymbol> symbols2 : this.currentDependencyOnTile.symbols) {

					this.rect1 = new android.graphics.Rect((int) symbols2.point.pointX, (int) (symbols2.point.pointY),
							(int) (symbols2.point.pointX + symbols2.value.symbol.getWidth()),
							(int) (symbols2.point.pointY + symbols2.value.symbol.getHeight()));

					for (int y = 0; y < refPos.length; y++) {
						if (refPos[y] != null) {
							this.rect2 = new android.graphics.Rect((int) refPos[y].x,
									(int) (refPos[y].y - refPos[y].height), (int) (refPos[y].x + refPos[y].width),
									(int) (refPos[y].y));

							if (android.graphics.Rect.intersects(this.rect2, this.rect1)) {
								refPos[y] = null;
							}
						}
					}
				}
			}
		}
	}

	void removeSymbolsFromDrawnAreas(List<SymbolContainer> symbols) {
		Tile lefttmp = new Tile(this.currentTile.tileX - 1, this.currentTile.tileY, this.currentTile.zoomLevel);
		Tile righttmp = new Tile(this.currentTile.tileX + 1, this.currentTile.tileY, this.currentTile.zoomLevel);
		Tile uptmp = new Tile(this.currentTile.tileX, this.currentTile.tileY - 1, this.currentTile.zoomLevel);
		Tile downtmp = new Tile(this.currentTile.tileX, this.currentTile.tileY + 1, this.currentTile.zoomLevel);

		boolean up;
		boolean left;
		boolean right;
		boolean down;

		this.tmp = this.dependencyTable.get(lefttmp);
		left = this.tmp == null ? false : this.tmp.drawn;

		this.tmp = this.dependencyTable.get(righttmp);
		right = this.tmp == null ? false : this.tmp.drawn;

		this.tmp = this.dependencyTable.get(uptmp);
		up = this.tmp == null ? false : this.tmp.drawn;

		this.tmp = this.dependencyTable.get(downtmp);
		down = this.tmp == null ? false : this.tmp.drawn;

		SymbolContainer ref;

		for (int i = 0; i < symbols.size(); i++) {
			ref = symbols.get(i);

			if (up && ref.y < 0) {
				symbols.remove(i);
				i--;
				continue;
			}

			if (down && ref.y + ref.symbol.getHeight() > Tile.TILE_SIZE) {
				symbols.remove(i);
				i--;
				continue;
			}
			if (left && ref.x < 0) {
				symbols.remove(i);
				i--;
				continue;
			}
			if (right && ref.x + ref.symbol.getWidth() > Tile.TILE_SIZE) {
				symbols.remove(i);
				i--;
				continue;
			}
		}
	}
}
