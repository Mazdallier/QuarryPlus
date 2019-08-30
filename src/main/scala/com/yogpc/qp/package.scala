package com.yogpc

import cats._
import cats.data._
import cats.implicits._
import net.minecraft.enchantment.Enchantment
import net.minecraft.item.ItemStack
import net.minecraft.nbt._
import net.minecraft.util.math.BlockPos
import net.minecraft.util.text.StringTextComponent
import net.minecraft.util.{Direction, ResourceLocation}
import net.minecraftforge.common.util.Constants.NBT
import net.minecraftforge.common.util.{INBTSerializable, LazyOptional, NonNullSupplier}
import net.minecraftforge.fluids.FluidStack
import net.minecraftforge.registries.ForgeRegistries

package object qp {

  type NBTWrapper[A, NBTType <: INBT] = A => NBTType
  type Cap[T] = OptionT[Eval, T]

  val enchantCollector: PartialFunction[(ResourceLocation, Int), (ResourceLocation, Integer)] = {
    case (a, b) if b > 0 => (a, Int.box(b))
  }
  val toComponentString: String => StringTextComponent = s => new StringTextComponent(s)
  val nonNull: AnyRef => Boolean = obj => obj != null
  val facings = Eval.later(Direction.values()).map(_.toList)

  def toJavaOption[T](o: Option[T]): java.util.Optional[T] = {
    //I think it's faster than match function.
    if (o.isDefined) {
      java.util.Optional.ofNullable(o.get)
    } else {
      java.util.Optional.empty()
    }
  }

  def toScalaOption[T](o: java.util.Optional[T]): Option[T] = {
    if (o.isPresent) {
      Some(o.get())
    } else {
      None
    }
  }

  implicit class SOM[T](val o: java.util.Optional[T]) extends AnyVal {
    def scalaMap[B](f: T => B): Option[B] = toScalaOption(o).map(f)

    def scalaFilter(p: T => Boolean): Option[T] = toScalaOption(o).filter(p)

    def asScala: Option[T] = toScalaOption(o)
  }

  implicit class JOS[T](val o: Option[T]) extends AnyVal {
    def asJava: java.util.Optional[T] = toJavaOption(o)
  }

  implicit class ItemStackRemoveEnchantment(val stack: ItemStack) extends AnyVal {
    def removeEnchantment(enchantment: Enchantment): Unit = {
      val id = ForgeRegistries.ENCHANTMENTS.getKey(enchantment)
      val tagName = if (stack.getItem == net.minecraft.item.Items.ENCHANTED_BOOK) "StoredEnchantments" else "ench"
      val list = Option(stack.getTag).fold(new ListNBT)(_.getList(tagName, NBT.TAG_COMPOUND))

      import scala.collection.JavaConverters._
      val copied = list.asScala.zipWithIndex.collect { case (t:CompoundNBT, i) => (t, i) }
      for ((tag, i) <- copied) {
        if (tag.getString("id") == id.toString) {
          list.remove(i)
        }
      }
      Option(stack.getTag).foreach(subtag => {
        subtag.remove(tagName)
        if (!list.isEmpty) {
          subtag.put(tagName, list)
        }
        stack.setTag(if (subtag.isEmpty) null else subtag)
      })
    }

    def enchantmentAdded(enchantment: Enchantment, level: Int): ItemStack = {
      stack.addEnchantment(enchantment, level)
      stack
    }
  }

  implicit val Long2NBT: NBTWrapper[Long, LongNBT] = (num: Long) => new LongNBT(num)
  implicit val int2NBT: NBTWrapper[Int, IntNBT] = (num: Int) => new IntNBT(num)
  implicit val bool2NBT: NBTWrapper[Boolean, ByteNBT] = (b: Boolean) => new ByteNBT(if (b) 1 else 0)

  implicit val Fluid2NBT: NBTWrapper[FluidStack, CompoundNBT] = (num: FluidStack) => num.writeToNBT(new CompoundNBT)
  implicit val NBTSerializable2NBT: INBTSerializable[CompoundNBT] NBTWrapper CompoundNBT = _.serializeNBT()

  implicit class NumberToNbt[A](private val num: A) extends AnyVal {
    def toNBT[B <: INBT](implicit wrapper: NBTWrapper[A, B]): B = wrapper apply num
  }

  implicit class PosHelper(val blockPos: BlockPos) extends AnyVal {
    def offset(facing1: Direction, facing2: Direction): BlockPos = {
      val x = facing1.getXOffset + facing2.getXOffset
      val y = facing1.getYOffset + facing2.getYOffset
      val z = facing1.getZOffset + facing2.getZOffset
      blockPos.add(x, y, z)
    }

    def copy(x: Int = blockPos.getX, y: Int = blockPos.getY, z: Int = blockPos.getZ): BlockPos = {
      if (x == blockPos.getX && y == blockPos.getY && z == blockPos.getZ) {
        blockPos.toImmutable
      } else {
        new BlockPos(x, y, z)
      }
    }
  }

  def transform0[T](cap: LazyOptional[T]) = Eval.always {
    if (cap.isPresent) {
      cap.orElseThrow(thrower).some
    } else {
      None
    }
  }

  implicit class AsScalaLO[T](val cap: LazyOptional[T]) extends AnyVal {
    def asScala: Cap[T] = OptionT(transform0(cap))
  }

  private val thrower: NonNullSupplier[AssertionError] = () =>
    new AssertionError(
      "LazyOptional has no content " +
        "though it returned true when isPresent is called.")

  implicit val showPos: Show[BlockPos] = pos => s"(${pos.getX}, ${pos.getY}, ${pos.getZ})"
}
