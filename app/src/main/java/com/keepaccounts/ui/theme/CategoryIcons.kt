package com.keepaccounts.ui.theme

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Assignment
import androidx.compose.material.icons.outlined.CardGiftcard
import androidx.compose.material.icons.outlined.ChildCare
import androidx.compose.material.icons.outlined.Checkroom
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Flight
import androidx.compose.material.icons.outlined.House
import androidx.compose.material.icons.outlined.LocalBar
import androidx.compose.material.icons.outlined.LocalHospital
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Pets
import androidx.compose.material.icons.outlined.PlayCircleOutline
import androidx.compose.material.icons.outlined.Redeem
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material.icons.outlined.Spa
import androidx.compose.material.icons.outlined.TrendingUp
import androidx.compose.material.icons.outlined.Undo
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material.icons.outlined.Wallet
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * 分类的图标。
 *
 * 数据库里存的是 `icon` 字符串键（"food" / "transport" ...），这里做一次到矢量图的映射。
 * 存键而不是存资源 id，是因为分类可以被用户改名、重建，键是稳定的语义标识。
 */
object CategoryIcons {

    fun of(key: String?): ImageVector = when (key) {
        "food" -> Icons.Outlined.Restaurant
        "transport" -> Icons.Outlined.DirectionsCar
        "shopping" -> Icons.Outlined.ShoppingBag
        "home" -> Icons.Outlined.House
        "phone" -> Icons.Outlined.Smartphone
        "fun" -> Icons.Outlined.PlayCircleOutline
        "medical" -> Icons.Outlined.LocalHospital
        "study" -> Icons.Outlined.School
        "gift" -> Icons.Outlined.CardGiftcard
        "pet" -> Icons.Outlined.Pets
        "travel" -> Icons.Outlined.Flight
        "digital" -> Icons.Outlined.Devices
        "cloth" -> Icons.Outlined.Checkroom
        "beauty" -> Icons.Outlined.Spa
        "sport" -> Icons.Outlined.FitnessCenter
        "drink" -> Icons.Outlined.LocalBar
        "baby" -> Icons.Outlined.ChildCare
        "insurance" -> Icons.Outlined.VerifiedUser
        "tax" -> Icons.Outlined.AccountBalance
        "salary" -> Icons.Outlined.Payments
        "bonus" -> Icons.Outlined.EmojiEvents
        "redpacket" -> Icons.Outlined.Redeem
        "invest" -> Icons.Outlined.TrendingUp
        "reimburse" -> Icons.Outlined.Assignment
        "refund" -> Icons.Outlined.Undo
        "wallet", "cash" -> Icons.Outlined.Wallet
        else -> Icons.Outlined.Apps
    }
}
