/*
 * Copyright (C) 2012,2013 yogpstop This program is free software: you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License along with this program.
 * If not, see <http://www.gnu.org/licenses/>.
 */

package com.yogpc.qp.tile;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import com.yogpc.qp.PowerManager;
import javax.annotation.Nullable;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTank;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.FluidTankProperties;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidTankProperties;

/**
 * See {@link buildcraft.factory.tile.TileDistiller_BC8}, {@link buildcraft.api.recipes.IRefineryRecipeManager}, {@link buildcraft.energy.BCEnergyRecipes}
 * TODO 3tanks UP for gus, down for liquid, side for input
 */
public class TileRefinery extends APowerTile implements IEnchantableTile {
    public final DistillerTank horizontalsTank = new DistillerTank();
    public final DistillerTank upTank = new DistillerTank();
    public final DistillerTank downTank = new DistillerTank();
    private List<DistillerTank> tanks = Arrays.asList(horizontalsTank, upTank, downTank);
    private final AllTanks fluidHandler = new AllTanks();

    public FluidStack res;
    public final FluidStack[] src = new FluidStack[2];
    public double rem_energy;
    public long rem_time;
    public FluidStack cached;

    public float animationSpeed = 1;
    private int animationStage = 0;

    protected byte unbreaking;
    protected byte fortune;
    protected boolean silktouch;
    protected byte efficiency;

    public int buf;

    @Override
    public void G_reinit() {
        PowerManager.configureRefinery(this, this.efficiency, this.unbreaking);
        tanks.forEach(distillerTank -> distillerTank.setCapacity((int) (Fluid.BUCKET_VOLUME * 4 * Math.pow(1.3, this.fortune))));
    }

    @Override
    public void readFromNBT(final NBTTagCompound nbttc) {
        super.readFromNBT(nbttc);
        this.silktouch = nbttc.getBoolean("silktouch");
        this.fortune = nbttc.getByte("fortune");
        this.efficiency = nbttc.getByte("efficiency");
        this.unbreaking = nbttc.getByte("unbreaking");
        final NBTTagList srcl = nbttc.getTagList("src", 10);
        for (int i = 0; i < srcl.tagCount(); ++i) {
            final NBTTagCompound srct = srcl.getCompoundTagAt(i);
            final int j = srct.getByte("Slot") & 255;
            if (j < this.src.length)
                this.src[j] = FluidStack.loadFluidStackFromNBT(srct);
        }
        this.res = FluidStack.loadFluidStackFromNBT(nbttc.getCompoundTag("res"));
        this.cached = FluidStack.loadFluidStackFromNBT(nbttc.getCompoundTag("cached"));
        this.rem_energy = nbttc.getDouble("rem_energy");
        this.rem_time = nbttc.getLong("rem_time");
        this.animationSpeed = nbttc.getFloat("animationSpeed");
        this.animationStage = nbttc.getInteger("animationStage");
        this.buf = (int) (Fluid.BUCKET_VOLUME * 4 * Math.pow(1.3, this.fortune));
        PowerManager.configureRefinery(this, this.efficiency, this.unbreaking);
    }

    @Override
    public NBTTagCompound writeToNBT(final NBTTagCompound nbttc) {
        nbttc.setBoolean("silktouch", this.silktouch);
        nbttc.setByte("fortune", this.fortune);
        nbttc.setByte("efficiency", this.efficiency);
        nbttc.setByte("unbreaking", this.unbreaking);
        final NBTTagList srcl = new NBTTagList();
        for (int i = 0; i < this.src.length; ++i)
            if (this.src[i] != null) {
                final NBTTagCompound srct = new NBTTagCompound();
                srct.setByte("Slot", (byte) i);
                this.src[i].writeToNBT(srct);
                srcl.appendTag(srct);
            }
        nbttc.setTag("src", srcl);
        if (this.res != null)
            nbttc.setTag("res", this.res.writeToNBT(new NBTTagCompound()));
        if (this.cached != null)
            nbttc.setTag("cached", this.cached.writeToNBT(new NBTTagCompound()));
        nbttc.setDouble("rem_energy", this.rem_energy);
        nbttc.setLong("rem_time", this.rem_time);
        nbttc.setFloat("animationSpeed", this.animationSpeed);
        nbttc.setInteger("animationStage", this.animationStage);
        return super.writeToNBT(nbttc);
    }

    @Override
    public void update() {
        super.update();/*
        if (getWorld().isRemote) {
            simpleAnimationIterate();
            return;
        }
        if (getWorld().getWorldTime() % 20 == 7)
            PacketHandler.sendPacketToAround(new YogpstopPacket(this),
                    getWorld().provider.getDimension(), getPos().getX(), getPos().getY(), getPos().getZ());
        if (this.cached == null) {
            decreaseAnimation();
            return;
        }
        if (this.rem_time > 0)
            this.rem_time--;
        if (this.rem_time > 0
                || !PowerManager.useEnergyRefinery(this, this.rem_energy, this.unbreaking, this.efficiency)) {
            decreaseAnimation();
            return;
        }
        increaseAnimation();
        if (this.res == null)
            this.res = this.cached.copy();
        else
            this.res.amount += this.cached.amount;
        this.cached = null;
        decreaseAnimation();*/
//        RefineryRecipeHelper.get(this);
    }

    @Override
    protected boolean isWorking() {
        return false;
    }

    public int getAnimationStage() {
        return this.animationStage;
    }

    private void simpleAnimationIterate() {
        if (this.animationSpeed > 1) {
            this.animationStage += this.animationSpeed;

            if (this.animationStage > 300)
                this.animationStage = 100;
        } else if (this.animationStage > 0)
            this.animationStage--;
    }

    private void sendNowPacket() {
        //TODO crate now packet [DATA]???
    }

    private void increaseAnimation() {
        final float prev = this.animationSpeed;
        if (this.animationSpeed < 2)
            this.animationSpeed = 2;
        else if (this.animationSpeed <= 5)
            this.animationSpeed += 0.1;

        this.animationStage += this.animationSpeed;

        if (this.animationStage > 300)
            this.animationStage = 100;
        if (this.animationSpeed != prev)
            sendNowPacket();
    }

    private void decreaseAnimation() {
        final float prev = this.animationSpeed;
        if (this.animationSpeed >= 1) {
            this.animationSpeed -= 0.1;

            this.animationStage += this.animationSpeed;

            if (this.animationStage > 300)
                this.animationStage = 100;
        } else if (this.animationStage > 0)
            this.animationStage--;
        if (this.animationSpeed != prev)
            sendNowPacket();
    }

    /*
        @Override
        public void C_recievePacket(final byte id, final byte[] data, final EntityPlayer ep) {
            final ByteArrayDataInput badi = ByteStreams.newDataInput(data);
            switch (id) {
                case PacketHandler.StC_NOW:
                    this.animationSpeed = badi.readFloat();
                    break;
            }
        }

    @Override
    public int fill(final FluidStack resource, final boolean doFill) {
        for (final FluidStack s : this.src) {
            if (!resource.isFluidEqual(s))
                continue;
            final int ret = Math.min(this.buf - s.amount, resource.amount);
            if (doFill)
                s.amount += ret;
//            RefineryRecipeHelper.get(this);
            return ret;
        }
        for (int i = this.src.length - 1; i >= 0; i--) {
            if (this.src[i] != null)
                continue;
            final int ret = Math.min(this.buf, resource.amount);
            if (doFill) {
                this.src[i] = resource.copy();
                this.src[i].amount = ret;
            }
//            RefineryRecipeHelper.get(this);
            return ret;
        }
        return 0;
    }

    @Override
    public FluidStack drain(final FluidStack resource, final boolean doDrain) {
        if (resource == null)
            return null;
        if (resource.isFluidEqual(this.res))
            return drain(resource.amount, doDrain);
        for (int i = this.src.length - 1; i >= 0; i--) {
            if (!resource.isFluidEqual(this.src[i]))
                continue;
            final FluidStack ret = this.src[i].copy();
            ret.amount = Math.min(resource.amount, ret.amount);
            if (doDrain) {
                this.src[i].amount -= ret.amount;
                if (this.src[i].amount == 0)
                    this.src[i] = null;
            }
//            RefineryRecipeHelper.get(this);
            return ret;
        }
        return null;
    }

    @Override
    public FluidStack drain(final int maxDrain, final boolean doDrain) {
        if (this.res == null)
            return null;
        final FluidStack ret = this.res.copy();
        ret.amount = Math.min(maxDrain, ret.amount);
        if (doDrain) {
            this.res.amount -= ret.amount;
            if (this.res.amount == 0)
                this.res = null;
        }
//        RefineryRecipeHelper.get(this);
        return ret;
    }

    @Override
    public IFluidTankProperties[] getTankProperties() {
        final IFluidTankProperties[] ret = new IFluidTankProperties[this.src.length + 1];
        ret[0] = new FluidTankProperties(this.res, this.buf);
        for (int i = this.src.length - 1; i >= 0; i--)
            ret[i + 1] = new FluidTankProperties(this.src[i], this.buf);
        return ret;
    }
*/
    @Override
    public Map<Integer, Byte> getEnchantments() {
        final Map<Integer, Byte> ret = new HashMap<>();
        if (this.efficiency > 0)
            ret.put(EfficiencyID, this.efficiency);
        if (this.fortune > 0)
            ret.put(FortuneID, this.fortune);
        if (this.unbreaking > 0)
            ret.put(UnbreakingID, this.unbreaking);
        if (this.silktouch)
            ret.put(SilktouchID, (byte) 1);
        return ret;
    }

    @Override
    public void setEnchantent(final short id, final short val) {
        if (id == EfficiencyID)
            this.efficiency = (byte) val;
        else if (id == FortuneID)
            this.fortune = (byte) val;
        else if (id == UnbreakingID)
            this.unbreaking = (byte) val;
        else if (id == SilktouchID && val > 0)
            this.silktouch = true;
    }

    @Override
    public boolean hasCapability(Capability<?> capability, @Nullable EnumFacing facing) {
        return capability == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY || super.hasCapability(capability, facing);
    }

    @Nullable
    @Override
    public <T> T getCapability(Capability<T> capability, @Nullable EnumFacing facing) {
        if (capability == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY) {
            IFluidHandler handler;
            if (facing == null) {
                handler = fluidHandler;
            } else if (facing == EnumFacing.DOWN) {
                handler = downTank;
            } else if (facing == EnumFacing.UP) {
                handler = upTank;
            } else {
                handler = horizontalsTank;
            }
            return CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY.cast(handler);
        } else {
            return super.getCapability(capability, facing);
        }
    }

    private class AllTanks implements IFluidHandler {

        /**
         * Returns an array of objects which represent the internal tanks.
         * These objects cannot be used to manipulate the internal tanks.
         *
         * @return Properties for the relevant internal tanks.
         */
        @Override
        public IFluidTankProperties[] getTankProperties() {
            IFluidTankProperties[] array = tanks.stream().flatMap(distillerTank -> Stream.of(distillerTank.getTankProperties())).toArray(IFluidTankProperties[]::new);
            if (array.length == 0) {
                return new IFluidTankProperties[]{new FluidTankProperties(null, horizontalsTank.getCapacity(), false, false)};
            } else {
                return array;
            }
        }

        /**
         * Fills fluid into internal tanks, distribution is left entirely to the IFluidHandler.
         *
         * @param resource FluidStack representing the Fluid and maximum amount of fluid to be filled.
         * @param doFill   If false, fill will only be simulated.
         * @return Amount of resource that was (or would have been, if simulated) filled.
         */
        @Override
        public int fill(FluidStack resource, boolean doFill) {
            return 0;
        }

        /**
         * Drains fluid out of internal tanks, distribution is left entirely to the IFluidHandler.
         *
         * @param resource FluidStack representing the Fluid and maximum amount of fluid to be drained.
         * @param doDrain  If false, drain will only be simulated.
         * @return FluidStack representing the Fluid and amount that was (or would have been, if
         * simulated) drained.
         */
        @Nullable
        @Override
        public FluidStack drain(FluidStack resource, boolean doDrain) {
            return null;
        }

        /**
         * Drains fluid out of internal tanks, distribution is left entirely to the IFluidHandler.
         * <p/>
         * This method is not Fluid-sensitive.
         *
         * @param maxDrain Maximum amount of fluid to drain.
         * @param doDrain  If false, drain will only be simulated.
         * @return FluidStack representing the Fluid and amount that was (or would have been, if
         * simulated) drained.
         */
        @Nullable
        @Override
        public FluidStack drain(int maxDrain, boolean doDrain) {
            return null;
        }
    }

    private class DistillerTank extends FluidTank {

        public DistillerTank() {
            super(4 * Fluid.BUCKET_VOLUME);
        }

        @Override
        public boolean canFillFluidType(FluidStack fluid) {
            return super.canFillFluidType(fluid);
        }
    }
}
