/**
 *  Copyright (C) 2002-2011  The FreeCol Team
 *
 *  This file is part of FreeCol.
 *
 *  FreeCol is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 2 of the License, or
 *  (at your option) any later version.
 *
 *  FreeCol is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with FreeCol.  If not, see <http://www.gnu.org/licenses/>.
 */

package net.sf.freecol.server.model;

import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.logging.Logger;

import net.sf.freecol.common.model.Game;
import net.sf.freecol.common.model.GoodsContainer;
import net.sf.freecol.common.model.GoodsType;
import net.sf.freecol.common.model.IndianSettlement;
import net.sf.freecol.common.model.Player;
import net.sf.freecol.common.model.Specification;
import net.sf.freecol.common.model.Tile;
import net.sf.freecol.common.model.Unit;
import net.sf.freecol.common.model.UnitType;
import net.sf.freecol.common.util.Utils;
import net.sf.freecol.server.control.ChangeSet;


/**
 * The server version of an Indian Settlement.
 */
public class ServerIndianSettlement extends IndianSettlement
    implements ServerModelObject {

    private static final Logger logger = Logger.getLogger(ServerIndianSettlement.class.getName());

    public static final int MAX_HORSES_PER_TURN = 2;


    /**
     * Trivial constructor for all ServerModelObjects.
     */
    public ServerIndianSettlement(Game game, String id) {
        super(game, id);
    }

    /**
     * Creates a new ServerIndianSettlement.
     *
     * @param game The <code>Game</code> in which this object belong.
     * @param owner The <code>Player</code> owning this settlement.
     * @param name The name for this settlement.
     * @param tile The location of the <code>IndianSettlement</code>.
     * @param isCapital True if settlement is tribe's capital
     * @param learnableSkill The skill that can be learned by
     *     Europeans at this settlement.
     * @param spokenTo Indicates if any European scout has asked to
     *     speak with the chief.
     * @param missionary The missionary in this settlement (or null).
     * @exception IllegalArgumentException if an invalid tribe or kind is given
     */
    public ServerIndianSettlement(Game game, Player owner, String name,
                                  Tile tile, boolean isCapital,
                                  UnitType learnableSkill,
                                  Set<Player> spokenTo, Unit missionary) {
        super(game, owner, name, tile);

        setGoodsContainer(new GoodsContainer(game, this));
        this.learnableSkill = learnableSkill;
        setCapital(isCapital);
        this.spokenTo = spokenTo;
        this.missionary = missionary;

        convertProgress = 0;
        updateWantedGoods();
    }


    /**
     * New turn for this native settlement.
     *
     * @param random A <code>Random</code> number source.
     * @param cs A <code>ChangeSet</code> to update.
     */
    public void csNewTurn(Random random, ChangeSet cs) {
        logger.finest("ServerIndianSettlement.csNewTurn, for " + toString());
        ServerPlayer owner = (ServerPlayer) getOwner();
        Specification spec = getSpecification();

        // Produce goods.
        List<GoodsType> goodsList = spec.getGoodsTypeList();
        for (GoodsType g : goodsList) {
            addGoods(g.getStoredAs(), getProductionOf(g));
        }

        // Consume goods
        // TODO: do we need this at all? At the moment, most Indian Settlements
        // consume more than they produce.
        for (GoodsType g : goodsList) {
            consumeGoods(g, getConsumptionOf(g));
        }

        // Do a little ad hoc manufacturing.
        for (GoodsType g : goodsList) {
            GoodsType produced;
            if (g.isRawMaterial()
                && (produced = g.getProducedMaterial()) != null
                && produced.isStorable()
                && getGoodsCount(g) > getWantedGoodsAmount(g)
                && getGoodsCount(produced) < getWantedGoodsAmount(produced)) {
                // Say, 1/3 of the units present make quantity 1 of the item.
                // Leaving efficiency at 1:1 ATM.
                int production = getUnitCount() / 3;
                if (production > 0) {
                    removeGoods(g, production);
                    addGoods(produced, production);
                }
            }
        }

        // Check for new resident.
        // Alcohol also contributes to create children.
        GoodsType foodType = spec.getPrimaryFoodType();
        GoodsType rumType = spec.getGoodsType("model.goods.rum");
        List<UnitType> unitTypes
            = spec.getUnitTypesWithAbility("model.ability.bornInIndianSettlement");
        if (!unitTypes.isEmpty()
            && (getGoodsCount(foodType) + 4 * getGoodsCount(rumType)
                > FOOD_PER_COLONIST + KEEP_RAW_MATERIAL)
            && ownedUnits.size() <= getType().getMaximumSize()) {
            // Allow one more brave than the initially generated number.
            // This is more than sufficient. Do not increase the amount
            // without discussing it on the developer's mailing list first.
            UnitType type = Utils.getRandomMember(logger, "Choose birth",
                                                  unitTypes, random);
            Unit unit = new ServerUnit(getGame(), getTile(), owner, type);
            consumeGoods(foodType, FOOD_PER_COLONIST);
            consumeGoods(rumType, FOOD_PER_COLONIST/4);
            // New units quickly go out of their city and start annoying.
            addOwnedUnit(unit);
            unit.setIndianSettlement(this);
            logger.info("New native created in " + getName()
                        + " with ID=" + unit.getId());
        }

        // Try to breed horses
        // TODO: Make this generic.
        GoodsType horsesType = spec.getGoodsType("model.goods.horses");
        // TODO: remove this
        GoodsType grainType = spec.getGoodsType("model.goods.grain");
        int foodProdAvail = getProductionOf(grainType) - getFoodConsumption();
        if (getGoodsCount(horsesType) >= horsesType.getBreedingNumber()
            && foodProdAvail > 0) {
            int nHorses = Math.min(MAX_HORSES_PER_TURN, foodProdAvail);
            addGoods(horsesType, nHorses);
            logger.finest("Settlement " + getName() + " bred " + nHorses);
        }

        getGoodsContainer().removeAbove(getWarehouseCapacity());
        updateWantedGoods();
    }

    /**
     * Convenience function to remove an amount of goods.
     *
     * @param type The <code>GoodsType</code> to remove.
     * @param amount The amount of goods to remove.
     */
    private void consumeGoods(GoodsType type, int amount) {
        if (getGoodsCount(type) > 0) {
            amount = Math.min(amount, getGoodsCount(type));
            removeGoods(type, amount);
        }
    }


    /**
     * Returns the tag name of the root element representing this object.
     *
     * @return "serverIndianSettlement"
     */
    public String getServerXMLElementTagName() {
        return "serverIndianSettlement";
    }
}
