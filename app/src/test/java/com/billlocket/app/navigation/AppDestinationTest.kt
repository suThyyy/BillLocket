package com.billlocket.app.navigation

import com.billlocket.app.domain.model.DraftId
import com.billlocket.app.domain.model.TransactionId
import com.billlocket.app.domain.model.TransactionImageId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppDestinationTest {
    @Test
    fun `home is the first top level destination`() {
        assertEquals(TopLevelDestination.Home, TopLevelDestination.entries.first())
        assertEquals("home", AppDestination.Home.route)
    }

    @Test
    fun `capture carries only a validated draft id`() {
        assertEquals("capture/draft-123", AppDestination.Capture(DraftId("draft-123")).route)
        assertEquals(
            "transaction/transaction-1/edit/draft-123",
            AppDestination.EditTransaction(TransactionId("transaction-1"), DraftId("draft-123")).route,
        )
    }

    @Test
    fun `unsafe and malformed route ids are rejected at boundary`() {
        assertNull(parseRouteId(null))
        assertNull(parseRouteId(""))
        assertNull(parseRouteId("has/slash"))
        assertNull(parseRouteId("has space"))
        assertNull(parseRouteId("a".repeat(129)))
        assertEquals("valid_ID-1.0", parseRouteId("valid_ID-1.0"))
    }

    @Test
    fun `only exact top level routes select a tab`() {
        assertEquals(TopLevelDestination.Home, TopLevelDestination.fromRoute("home"))
        assertEquals(TopLevelDestination.Statistics, TopLevelDestination.fromRoute("statistics"))
        assertEquals(TopLevelDestination.Accounts, TopLevelDestination.fromRoute("accounts"))
        assertEquals(TopLevelDestination.Map, TopLevelDestination.fromRoute("map"))
        assertEquals(
            TopLevelDestination.Map,
            TopLevelDestination.fromRoute(RoutePattern.MAP_ROUTE),
        )
        assertNull(TopLevelDestination.fromRoute("more"))
        assertNull(TopLevelDestination.fromRoute("capture/{draftId}"))
        assertNull(TopLevelDestination.fromRoute("transaction/{transactionId}"))
    }

    @Test
    fun `map route can focus a specific transaction image`() {
        assertEquals(
            "map?transactionId=transaction-1&imageId=image-1",
            AppDestination.Map(
                transactionId = TransactionId("transaction-1"),
                imageId = TransactionImageId("image-1"),
            ).route,
        )
    }

    @Test
    fun `monthly recap route formats monthEpochDay safely or falls back to base route`() {
        assertEquals("monthly-recap", AppDestination.MonthlyRecap().route)
        assertEquals("monthly-recap?monthEpochDay=20728", AppDestination.MonthlyRecap(20728L).route)
        assertNull(TopLevelDestination.fromRoute("monthly-recap"))
    }
}
