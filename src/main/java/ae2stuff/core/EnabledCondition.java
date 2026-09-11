/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2026 AE2 Stuff UD contributors
 */

package ae2stuff.core;

import java.util.function.BooleanSupplier;

import com.google.gson.JsonObject;

import net.minecraft.util.JsonUtils;
import net.minecraftforge.common.crafting.IConditionFactory;
import net.minecraftforge.common.crafting.JsonContext;

/**
 * {@code ae2stuff:enabled}: true while the machine named by {@code machine} is switched on in the config.
 */
public final class EnabledCondition implements IConditionFactory {

    @Override
    public BooleanSupplier parse(final JsonContext context, final JsonObject json) {
        final boolean enabled = AE2StuffConfig.instance().isEnabled(JsonUtils.getString(json, "machine"));
        return () -> enabled;
    }
}
