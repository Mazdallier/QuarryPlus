package com.yogpc.qp.machines.modules;

import java.util.function.Function;

import com.yogpc.qp.QuarryPlus;
import com.yogpc.qp.machines.base.APowerTile;
import com.yogpc.qp.machines.base.HasStorage;
import com.yogpc.qp.machines.base.IDisabled;
import com.yogpc.qp.machines.base.IModule;
import com.yogpc.qp.machines.pump.PumpModule;
import com.yogpc.qp.utils.Holder;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.init.Enchantments;
import net.minecraft.item.EnumRarity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import scala.Symbol;

public class ItemPumpModule extends Item implements IDisabled, IModuleItem {
    public static final Symbol SYMBOL = Symbol.apply("ModulePump");

    public ItemPumpModule() {
        super(new Properties().group(Holder.tab()).rarity(EnumRarity.UNCOMMON));
        setRegistryName(QuarryPlus.modID, QuarryPlus.Names.pumpModule);
    }

    @Override
    public Symbol getSymbol() {
        return SYMBOL;
    }

    @Override
    public <T extends APowerTile & HasStorage> Function<T, IModule> getModule(ItemStack stack) {
        return t -> PumpModule.fromModule(t, () -> EnchantmentHelper.getEnchantmentLevel(Enchantments.UNBREAKING, stack));
    }
}