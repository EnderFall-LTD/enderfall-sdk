package uk.co.enderfall.sdk.api.render;

/** Selects an item's resource-model display transform before the SDK's placement transform. */
@uk.co.enderfall.sdk.api.annotation.Experimental
public enum ItemRenderPose {
    NONE, FIXED, GROUND, GUI, HEAD,
    FIRST_PERSON_LEFT_HAND, FIRST_PERSON_RIGHT_HAND,
    THIRD_PERSON_LEFT_HAND, THIRD_PERSON_RIGHT_HAND
}
