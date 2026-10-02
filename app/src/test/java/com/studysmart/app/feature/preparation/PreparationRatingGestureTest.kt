package com.studysmart.app.feature.preparation

import com.studysmart.app.core.model.Rating
import org.junit.Assert.*
import org.junit.Test

class PreparationRatingGestureTest {
    @Test fun gestureStartRegionsRespectVisibleCardAndHeader() {
        val card = PreparationGestureBounds(0f, 20f, 200f, 400f)
        val header = PreparationGestureBounds(20f, 40f, 180f, 100f)
        val pad = PreparationGestureBounds(208f, 80f, 320f, 300f)
        assertEquals(PreparationGestureRegion.HEADER, preparationGestureRegion(50f, 60f, card, header, pad))
        assertEquals(PreparationGestureRegion.CARD_BODY, preparationGestureRegion(50f, 150f, card, header, pad))
        assertEquals(PreparationGestureRegion.RATING_PAD, preparationGestureRegion(250f, 150f, card, header, pad))
        assertEquals(PreparationGestureRegion.OUTSIDE, preparationGestureRegion(204f, 150f, card, header, pad))
        val scrolledHeader = PreparationGestureBounds(20f, -100f, 180f, -40f)
        assertEquals(PreparationGestureRegion.CARD_BODY, preparationGestureRegion(50f, 60f, card, scrolledHeader, pad))
        assertEquals(PreparationGestureRegion.OUTSIDE, preparationGestureRegion(Float.NaN, 60f, card, header, pad))
        assertFalse(PreparationGestureRegion.CARD_BODY.allowsVertical)
        assertTrue(PreparationGestureRegion.RATING_PAD.allowsVertical)
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
