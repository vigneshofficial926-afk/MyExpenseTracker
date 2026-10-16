package com.expensetracker.app.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Coffee
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LaptopMac
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.ui.graphics.vector.ImageVector

object CategoryIconHelper {

    fun getIcon(iconName: String): ImageVector {
        return when (iconName.lowercase()) {
            "restaurant" -> Icons.Default.Restaurant
            "local_cafe", "cafe" -> Icons.Default.LocalCafe
            "coffee" -> Icons.Default.Coffee
            "directions_car", "car", "fuel" -> Icons.Default.DirectionsCar
            "home", "rent" -> Icons.Default.Home
            "bolt", "electricity", "utilities" -> Icons.Default.Bolt
            "phone_android", "mobile" -> Icons.Default.PhoneAndroid
            "shopping_bag", "shopping" -> Icons.Default.ShoppingBag
            "local_hospital", "health", "medical" -> Icons.Default.LocalHospital
            "movie", "entertainment" -> Icons.Default.Movie
            "flight", "travel" -> Icons.Default.Flight
            "school", "education" -> Icons.Default.School
            "credit_card", "emi", "loan" -> Icons.Default.CreditCard
            "groups", "family" -> Icons.Default.Groups
            "subscriptions" -> Icons.Default.Subscriptions
            "account_balance_wallet", "wallet", "salary" -> Icons.Default.AccountBalanceWallet
            "account_balance", "bank" -> Icons.Default.AccountBalance
            "laptop_mac", "laptop", "freelance" -> Icons.Default.LaptopMac
            "trending_up", "business" -> Icons.Default.TrendingUp
            "savings", "interest" -> Icons.Default.Savings
            "card_giftcard", "gift" -> Icons.Default.CardGiftcard
            "attach_money", "money" -> Icons.Default.AttachMoney
            else -> Icons.Default.MoreHoriz
        }
    }

    val availableIcons = listOf(
        "restaurant", "coffee", "directions_car", "home", "bolt", "phone_android",
        "shopping_bag", "local_hospital", "movie", "flight", "school", "credit_card",
        "groups", "subscriptions", "account_balance_wallet", "laptop_mac",
        "trending_up", "savings", "card_giftcard", "attach_money", "more_horiz"
    )
}
