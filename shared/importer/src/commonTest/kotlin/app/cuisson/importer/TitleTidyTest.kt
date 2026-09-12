package app.cuisson.importer

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Every title here was imported from a real site during testing.
 */
class TitleTidyTest {

    @Test
    fun `drops the french marketing suffix`() {
        assertEquals(
            "Lasagnes a la bolognaise",
            TitleTidy.tidy("Lasagnes a la bolognaise : la meilleure recette"),
        )
        assertEquals(
            "Quiche lorraine maison",
            TitleTidy.tidy("Quiche lorraine maison : la meilleure recette"),
        )
        assertEquals("Tiramisu framboise", TitleTidy.tidy("Tiramisu framboise : la meilleure recette"))
    }

    @Test
    fun `drops the site name after a pipe`() {
        assertEquals("Chicken tikka masala", TitleTidy.tidy("Chicken tikka masala | BBC Good Food"))
    }

    @Test
    fun `drops the site name after a dash`() {
        assertEquals(
            "Fillet of beef wellington",
            TitleTidy.tidy("Fillet of beef wellington - Great British Chefs", "www.greatbritishchefs.com"),
        )
    }

    @Test
    fun `drops a trailing boast`() {
        assertEquals("Brioche perdue", TitleTidy.tidy("Brioche perdue facile et rapide"))
        assertEquals("Confiture de figues", TitleTidy.tidy("Confiture de figues facile"))
        assertEquals("Lasagna", TitleTidy.tidy("Lasagna (The Best)"))
    }

    @Test
    fun `leaves a title that is already a title`() {
        assertEquals("Beef Rendang", TitleTidy.tidy("Beef Rendang"))
        assertEquals("Vegetarian Chili", TitleTidy.tidy("Vegetarian Chili"))
        assertEquals("Crock-Pot Potato Soup", TitleTidy.tidy("Crock-Pot Potato Soup"))
        assertEquals("Best Lentil Soup", TitleTidy.tidy("Best Lentil Soup"))
    }

    @Test
    fun `does not eat a dash that belongs to the dish`() {
        assertEquals("Cocoa-Blackened Chicken Thighs", TitleTidy.tidy("Cocoa-Blackened Chicken Thighs"))
        assertEquals(
            "Chopped Salad With Sardines and Preserved Lemon",
            TitleTidy.tidy("Chopped Salad With Sardines and Preserved Lemon"),
        )
    }

    @Test
    fun `never returns nothing`() {
        assertEquals("Recipe", TitleTidy.tidy("Recipe"))
        assertEquals("la meilleure recette", TitleTidy.tidy("la meilleure recette"))
    }
}
