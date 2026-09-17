// Lithium by 2No2Name; LGPL-3.0. Via DivineMC@039981b0bf973ea9bee0f2521b2c01d9bb419802.
package net.caffeinemc.mods.lithium.common.util.change_tracking;

import net.minecraft.world.item.ItemStack;

public interface ChangePublisher<T> {
    void lithium$subscribe(ChangeSubscriber<T> subscriber, int subscriberData);

    int lithium$unsubscribe(ChangeSubscriber<T> subscriber);

    default void lithium$unsubscribeWithData(ChangeSubscriber<T> subscriber, int index) {
        throw new UnsupportedOperationException("Only implemented for ItemStacks");
    }

    default boolean lithium$isSubscribedWithData(ChangeSubscriber<ItemStack> subscriber, int subscriberData) {
        throw new UnsupportedOperationException("Only implemented for ItemStacks");
    }
}
