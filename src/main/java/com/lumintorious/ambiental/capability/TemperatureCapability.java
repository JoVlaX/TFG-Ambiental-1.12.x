package com.lumintorious.ambiental.capability;

import com.lumintorious.ambiental.TemperatureDamage;
import com.lumintorious.ambiental.TFCTemperatureConfig;
import com.lumintorious.ambiental.modifiers.BaseModifier;
import com.lumintorious.ambiental.modifiers.BlockModifier;
import com.lumintorious.ambiental.modifiers.EnvironmentalModifier;
import com.lumintorious.ambiental.modifiers.EquipmentModifier;
import com.lumintorious.ambiental.modifiers.ItemModifier;
import com.lumintorious.ambiental.modifiers.ModifierStorage;

import net.dries007.tfc.TerraFirmaCraft;
import net.dries007.tfc.api.capability.food.FoodStatsTFC;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityInject;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;

public class TemperatureCapability<C> implements ICapabilitySerializable<NBTTagCompound>, ITemperatureCapability
{
	@CapabilityInject(ITemperatureCapability.class)
    public static final Capability<ITemperatureCapability> CAPABILITY = null;
	
	
    /** The capability this is for */
    private final EntityPlayer player;
    
    public TemperatureCapability(EntityPlayer player)
    {
        this.player = player;
    }
    
	public static final BaseModifier CORE_TEMPERATURE = new BaseModifier("core", 0f, 0.0f);
	public boolean isRising;
	
	public static float AVERAGE = TFCTemperatureConfig.GENERAL.averageTemperature;
	public static float HOT_THRESHOLD = TFCTemperatureConfig.GENERAL.hotTemperature;
	public static float COOL_THRESHOLD = TFCTemperatureConfig.GENERAL.coldTemperature;
	public static float BURN_THRESHOLD = TFCTemperatureConfig.GENERAL.burningTemperature;
	public static float FREEZE_THRESHOLD = TFCTemperatureConfig.GENERAL.freezingTemperature;
	
	public ModifierStorage modifiers = new ModifierStorage();
	
	public float bodyTemperature = AVERAGE;
	
	public void clearModifiers() {
		this.modifiers = new ModifierStorage();
	}
	
	public void evaluateModifiers() {
		this.clearModifiers();
		ItemModifier.computeModifiers(player, modifiers);
		EnvironmentalModifier.computeModifiers(player, modifiers);
		BlockModifier.computeModifiers(player, modifiers);
		EquipmentModifier.getModifiers(player, modifiers);

		savedTarget = modifiers.getTargetTemperature();
		savedPotency = modifiers.getTotalPotency();
}
	
	public float savedTarget = AVERAGE;
	public float getTargetTemperature() {
		return savedTarget;
	}

	public static final float BAD_MULTIPLIER = 0.002f;
	public static final float GOOD_MULTIPLIER = 0.002f;
	public static final float CHANGE_CAP = 7.5f;
	public static final float HIGH_CHANGE = 0.20f;
	
	public float savedPotency = 1f;
	public float getPotency() {
		
		return savedPotency;
	}
	
	public float getTemperatureChange() {
		float target = getTargetTemperature();
		float speed = getPotency() * TFCTemperatureConfig.GENERAL.temperatureMultiplier;
		float change = Math.min(CHANGE_CAP, Math.max(-CHANGE_CAP, target - bodyTemperature));
		float newTemp = bodyTemperature + change;
		boolean isRising = true;
		if((bodyTemperature < AVERAGE && newTemp > bodyTemperature) || (bodyTemperature > AVERAGE && newTemp < bodyTemperature)) {
			speed *= GOOD_MULTIPLIER * TFCTemperatureConfig.GENERAL.positiveModifier;
		}else {
			speed *= BAD_MULTIPLIER * TFCTemperatureConfig.GENERAL.negativeModifier;
		}
		return ((float) change * speed);
	}
	
	public int tick = 0;
	public int damageTick = 0;
	
	public void update() {

		if(!player.world.isRemote) {
			this.setTemperature(this.getTemperature() + this.getTemperatureChange() / TFCTemperatureConfig.GENERAL.tickInterval);
			
			if (tick <= TFCTemperatureConfig.GENERAL.tickInterval) {
				tick++;
				return;
			} else {
				tick = 0;
				if (damageTick > 40) {
					damageTick = 0;
					if (TFCTemperatureConfig.GENERAL.takeDamage) {
						if(this.getTemperature() > BURN_THRESHOLD) {
								player.attackEntityFrom(TemperatureDamage.HEAT,  4f);
						}else if (this.getTemperature() < FREEZE_THRESHOLD){
								player.attackEntityFrom(TemperatureDamage.COLD, 4f);
						}
					}
					if (TFCTemperatureConfig.GENERAL.loseHungerThirst) {
						if (player.getFoodStats() instanceof FoodStatsTFC) {
							FoodStatsTFC stats = (FoodStatsTFC)player.getFoodStats();
							if (this.getTemperature() > (HOT_THRESHOLD * 2f + BURN_THRESHOLD) / 3f) {
								stats.addThirst(-8);
							} else if (this.getTemperature() < (COOL_THRESHOLD * 2f + FREEZE_THRESHOLD) / 3f){
								stats.setFoodLevel(stats.getFoodLevel() - 1);
							}
						}
						
					}
					
				} else {
					damageTick++;
				}
			}
			this.evaluateModifiers();
			updateAndSync();
		}

	}
	
	public String toString() {
		String str = "";
		for(BaseModifier modifier : modifiers) {
			str += modifier.getUnlocalizedName() + " -> " + modifier.getChange() + " @ " + modifier.getPotency() + "\n";
		}
		return String.format(
				"Body: %.1f ( %.4f )\n"
				+ "Target: %.1f \n"
				+ "Potency: %.4f",
				bodyTemperature,
				this.getTemperatureChange(),
				this.getTargetTemperature(),
				modifiers.getTotalPotency()
				) + "\n"+str;
	}

	@Override
	public float getTemperature() {
		return bodyTemperature;
	}
	
	@Override
	public void setTemperature(float newTemp) {
		if (newTemp < this.getTemperature()) {
			isRising = false;
		}else {
			isRising = true;
		}
		this.bodyTemperature = newTemp;
	}

	@Override
	public EntityPlayer getPlayer() {
		return player;
	}

	@Override
	public float getChange() {
		return getTemperatureChange();
	}
	
	public float getChangeSpeed() {
		return getPotency();
	}

	@Override
	public NBTTagCompound serializeNBT() {
		NBTTagCompound tag = new NBTTagCompound();
		tag.setFloat("temperature", this.getTemperature());
		tag.setFloat("target", this.getTargetTemperature());
		tag.setFloat("potency", this.getPotency());
		return tag;
	}

	@Override
	public void deserializeNBT(NBTTagCompound tag) {
		if(tag.hasKey("temperature")) {
			this.setTemperature(tag.getFloat("temperature"));
			this.savedTarget  = (tag.getFloat("target"));
			this.savedPotency = (tag.getFloat("potency"));

		}else {
			this.setTemperature(23.4f);
		}
	}
	
	public void updateAndSync() {
		EntityPlayer player = getPlayer();
        if (player instanceof EntityPlayerMP)
        {
            TerraFirmaCraft.getNetwork().sendTo(new TemperaturePacket(serializeNBT()), (EntityPlayerMP) player);
        }
	}
    
    @Override
    public boolean hasCapability(Capability<?> capability, EnumFacing facing)
    {
        return capability != null && capability == CAPABILITY;
    }

    @Override
    public <T> T getCapability(Capability<T> capability, EnumFacing facing)
    {
        return capability == CAPABILITY ? (T)(this) : null;
    }
}
