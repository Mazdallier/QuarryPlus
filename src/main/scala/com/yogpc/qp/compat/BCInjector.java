package com.yogpc.qp.compat;

import java.util.stream.Stream;

import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Direction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

class BCInjector implements IInjector {

//    @Optional.Method(modid = QuarryPlus.Optionals.BuildCraft_core)
    public static BCInjector init() {
        return null;
//        return new BCInjector(NoSpaceTransactor.INSTANCE);
    }

    // TEMP METHODS START
    @Override
    public Stream<? extends IInjector> getInjector(ItemStack stack, TileEntity entity, Direction facing) {
        return Stream.empty();
    }

    @Override
    public ItemStack inject(ItemStack stack, World world, BlockPos fromPos) {
        return stack;
    }
    //TEMP METHODS END

//    private final IItemTransactor transactor;

//    private BCInjector(IItemTransactor transactor) {
//        this.transactor = transactor;
//    }

    /*@Override
    @Optional.Method(modid = QuarryPlus.Optionals.BuildCraft_core)
    public Stream<? extends IInjector> getInjector(ItemStack stack, TileEntity entity, EnumFacing facing) {
        IItemTransactor transactor = ItemTransactorHelper.getTransactor(entity, facing.getOpposite());
        if (transactor != NoSpaceTransactor.INSTANCE) {
            return Stream.of(new BCInjector(transactor));
        }
        return Stream.empty();
    }

    @Override
    @Optional.Method(modid = QuarryPlus.Optionals.BuildCraft_core)
    public ItemStack inject(ItemStack stack, World world, BlockPos fromPos) {
        return transactor.insert(stack, false, false);
    }*/
}
