package com.studysmart.app.feature.preparation

import com.studysmart.app.core.model.Rating
import org.junit.Assert.*
import org.junit.Test

class PreparationRatingGestureTest {
    @Test fun scrollingVelocityIsVerticalSlowBoundedAndDensityAware() {
        assertEquals(0f, preparationJoystickScrollVelocity(0f, 8f, 8f, 1f), 0f)
        assertEquals(0f, preparationJoystickScrollVelocity(80f, 0f, 8f, 1f), 0f)
        assertEquals(0f, preparationJoystickScrollVelocity(80f, 80f, 8f, 1f), 0f)
        val slow = preparationJoystickScrollVelocity(0f, -9f, 8f, 1f)
        val faster = preparationJoystickScrollVelocity(0f, -36f, 8f, 1f)
        assertTrue(slow >= 20f && slow < faster)
        assertEquals(100f, preparationJoystickScrollVelocity(0f, -1000f, 8f, 1f), 0f)
        assertEquals(-200f, preparationJoystickScrollVelocity(0f, 1000f, 16f, 2f), 0f)
        assertEquals(0f, preparationJoystickScrollVelocity(Float.NaN, 80f, 8f, 1f), 0f)
        assertEquals(0f, preparationJoystickScrollVelocity(0f, -80f, 8f, Float.NaN), 0f)
    }
    @Test fun joystickReclassifiesDirectionsAndClearsCenterAndDiagonals() {
        assertEquals(RatingSwipeDirection.UP, ratingJoystickDirection(0f, -80f, 8f))
        assertEquals(RatingSwipeDirection.RIGHT, ratingJoystickDirection(80f, 0f, 8f))
        assertEquals(RatingSwipeDirection.DOWN, ratingJoystickDirection(0f, 80f, 8f))
        assertEquals(RatingSwipeDirection.LEFT, ratingJoystickDirection(-80f, 0f, 8f))
        assertNull(ratingJoystickDirection(0f, 0f, 8f))
        assertNull(ratingJoystickDirection(8f, 0f, 8f))
        assertNull(ratingJoystickDirection(80f, 80f, 8f))
        assertNull(ratingJoystickDirection(Float.NaN, 0f, 8f))
        assertFalse(ratingDragCommits(55f, 56f))
        assertTrue(ratingDragCommits(56f, 56f))
    }
    @Test fun previewIsSignedBoundedAndIndependentOfCommit() {
        assertEquals(16f, ratingPreviewDistance(1000f, 56f, 1f), 0f)
        assertEquals(-32f, ratingPreviewDistance(-1000f, 56f, 2f), 0f)
        assertEquals(8f, ratingPreviewDistance(28f, 56f, 1f), 0f)
        assertFalse(ratingDragCommits(28f, 56f))
        assertEquals(0f, ratingPreviewDistance(Float.NaN, 56f, 1f), 0f)
        assertEquals(0f, ratingPreviewDistance(28f, 56f, Float.NaN), 0f)
    }
    @Test fun directionsMatchThePhysicalRatingContract() {
        assertEquals(RatingSwipeDirection.LEFT, ratingDragDirection(-100f, 0f, 8f, false))
        assertEquals(RatingSwipeDirection.RIGHT, ratingDragDirection(100f, 0f, 8f, false))
        assertEquals(RatingSwipeDirection.UP, ratingDragDirection(0f, -100f, 8f, true))
        assertEquals(RatingSwipeDirection.DOWN, ratingDragDirection(0f, 100f, 8f, true))
        assertEquals(Rating.AGAIN, RatingSwipeDirection.LEFT.rating)
        assertEquals(Rating.EASY, RatingSwipeDirection.RIGHT.rating)
        assertEquals(Rating.GOOD, RatingSwipeDirection.UP.rating)
        assertEquals(Rating.HARD, RatingSwipeDirection.DOWN.rating)
    }
    @Test fun textScrollSlopAndDiagonalsDoNotClaimRating() {
        assertNull(ratingDragDirection(0f, 100f, 8f, false))
        assertNull(ratingDragDirection(5f, 0f, 8f, true))
        assertNull(ratingDragDirection(100f, 100f, 8f, true))
        assertNull(ratingDragDirection(Float.NaN, 0f, 8f, true))
    }
    @Test fun thresholdsAreBoundedAndNeverCommitShortOrInvalidDrags() {
        assertEquals(56f, ratingDragThreshold(100f, 1f), 0f)
        assertEquals(96f, ratingDragThreshold(10000f, 1f), 0f)
        assertFalse(ratingDragCommits(55f, 56f))
        assertTrue(ratingDragCommits(-56f, 56f))
        assertFalse(ratingDragCommits(Float.NaN, 56f))
        assertEquals(1f, ratingDragIntensity(200f, 56f), 0f)
        assertEquals(0f, ratingDragIntensity(10f, 0f), 0f)
    }
}
