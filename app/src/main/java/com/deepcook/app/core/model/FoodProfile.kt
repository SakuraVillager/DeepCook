package com.deepcook.app.core.model

import kotlinx.serialization.Serializable

@Serializable
data class FoodProfile(
    val cuisines: Set<String> = emptySet(),
    val spice: String? = null,
    val tastes: Set<String> = emptySet(),
    val diet: String? = null,
    /** null = unanswered; empty = explicitly no known allergies/intolerances. */
    val allergens: Set<String>? = null,
    val avoided: Set<String> = emptySet(),
    val experience: String? = null,
    val minutes: Int? = null,
    val servings: String? = null,
    val goals: Set<String> = emptySet(),
) {
    val hasAnswers: Boolean get() = cuisines.isNotEmpty() || spice != null || tastes.isNotEmpty() ||
        diet != null || allergens != null || avoided.isNotEmpty() || experience != null ||
        minutes != null || servings != null || goals.isNotEmpty()
}

@Serializable
data class FoodSetup(
    val profile: FoodProfile = FoodProfile(),
    val draft: FoodProfile? = null,
    val page: Int = 0,
    val completed: Boolean = false,
)

object FoodChoices {
    val cuisines = listOf("中式家常", "川湘风味", "粤式风味", "江浙风味", "日韩料理", "东南亚菜", "西式料理", "其他风味")
    val spices = listOf("不吃辣", "微辣", "中辣", "重辣", "都可以")
    val tastes = listOf("清淡", "鲜香", "酸甜", "浓郁", "都喜欢")
    val diets = listOf("不限制", "蛋奶素", "纯素")
    val allergens = listOf("牛奶及乳制品", "鸡蛋", "花生", "坚果", "大豆", "小麦", "鱼类", "虾蟹", "贝类", "芝麻")
    val avoided = listOf("香菜", "葱", "蒜", "芹菜", "胡萝卜", "茄子", "蘑菇", "动物内脏", "牛肉", "羊肉", "猪肉")
    val experience = listOf("刚开始学", "会做几道家常菜", "经常下厨")
    val goals = listOf("找到今天吃什么", "学会做新菜", "快手解决一餐", "尝试不同风味", "记录自己的拿手菜")
    val goalNotes = listOf("多一点日常做饭灵感", "从清晰的步骤开始", "优先发现省时的做法", "探索各地和世界料理", "整理、保存自己的做法")
}

private val ingredientAliases = mapOf(
    "牛奶及乳制品" to listOf("奶", "乳", "黄油", "芝士", "奶酪"),
    "鸡蛋" to listOf("蛋"),
    "花生" to listOf("花生"),
    "坚果" to listOf("核桃", "杏仁", "腰果", "榛子", "松子", "开心果", "碧根果", "坚果"),
    "大豆" to listOf("大豆", "黄豆", "豆腐", "豆浆", "豆皮", "腐竹", "豆瓣", "豆豉", "酱油", "生抽", "老抽", "味噌"),
    "小麦" to listOf("小麦", "面粉", "面包", "面条", "意面", "意大利面", "通心粉", "酱油", "生抽", "老抽", "面筋"),
    "鱼类" to listOf("鱼", "鳝", "鳗"),
    "虾蟹" to listOf("虾", "蟹"),
    "贝类" to listOf("贝", "蛤", "蚝", "牡蛎", "蛏", "蚌", "贻", "鲍"),
    "芝麻" to listOf("芝麻", "香油", "麻油"),
    "蘑菇" to listOf("蘑菇", "香菇", "口蘑", "金针菇", "杏鲍菇", "菌菇"),
    "动物内脏" to listOf("肝", "腰子", "猪肚", "牛肚", "肥肠", "鸡胗", "鸭胗", "内脏"),
    "牛肉" to listOf("牛肉", "牛腩", "牛排", "肥牛", "牛筋"),
    "羊肉" to listOf("羊肉", "羊排", "羊腿"),
    "猪肉" to listOf("猪肉", "五花", "里脊", "排骨", "培根", "火腿", "猪油", "肉末", "肉馅"),
)

private fun String.containsAny(terms: List<String>): Boolean = terms.any { contains(it, ignoreCase = true) }

/** Ingredient matching is a convenience filter, never a certification of allergen absence. */
fun Recipe.matchesFoodExclusions(profile: FoodProfile): Boolean {
    val names = ingredients.map { it.name.trim() }
    val restricted =
        !profile.allergens.isNullOrEmpty() || profile.avoided.isNotEmpty() || profile.diet in listOf("蛋奶素", "纯素")
    if (restricted && (names.isEmpty() || names.any { it.isBlank() || it == NO_CONTENT })) return false
    val excluded = profile.allergens.orEmpty() + profile.avoided
    if (excluded.any { term -> names.any { it.containsAny(ingredientAliases[term] ?: listOf(term)) } }) return false
    if (profile.spice == "不吃辣" && names.any { it.containsAny(listOf("辣椒", "小米椒", "干辣椒", "辣酱", "豆瓣酱")) }) return false
    if (profile.diet in listOf("蛋奶素", "纯素")) {
        val animal = listOf(
            "肉", "猪", "牛腩", "牛排", "肥牛", "羊", "鸡", "鸭", "鹅", "鱼", "虾", "蟹", "贝", "蚝", "蛤", "蛏", "鲍",
            "排骨", "培根", "火腿", "明胶", "猪油",
        )
        if (names.any { name ->
                name.replace("鸡蛋", "").replace("鸭蛋", "").replace("鹌鹑蛋", "").replace("鸡腿菇", "").containsAny(animal)
            }
        ) {
            return false
        }
    }
    if (profile.diet == "纯素" &&
        names.any { it.containsAny(listOf("蛋", "奶", "乳", "黄油", "芝士", "奶酪", "蜂蜜")) }
    ) {
        return false
    }
    return true
}

/** Stable sort: preferences only reorder the home feed; explicit search remains available. */
fun List<Recipe>.forFoodProfile(profile: FoodProfile): List<Recipe> {
    if (!profile.hasAnswers) return filter { it.featured }.take(6)
    fun Recipe.score(): Int {
        val text = (listOf(title, description) + ingredients.map { it.name }).joinToString(" ")
        val cuisineTerms = mapOf(
            "中式家常" to listOf("中式"),
            "川湘风味" to listOf("川", "湘", "麻婆", "宫保", "回锅", "辣子"),
            "粤式风味" to listOf("粤", "广式", "白切", "清蒸", "煲"),
            "江浙风味" to listOf("江浙", "杭", "浙", "苏式", "糖醋"),
            "日韩料理" to listOf("日式", "韩式"),
            "东南亚菜" to listOf("东南亚"),
            "西式料理" to listOf("美式", "欧式"),
            "其他风味" to listOf("其他"),
        )
        var points = if (featured) 1 else 0
        if (profile.cuisines.any { ("$region $text").containsAny(cuisineTerms[it].orEmpty()) }) points += 6
        if (profile.minutes != null && minutes != null && minutes <= profile.minutes) points += 4
        if (profile.experience == "刚开始学" && difficulty != null && difficulty <= 2) points += 4
        if ("快手解决一餐" in profile.goals && minutes != null && minutes <= 30) points += 4
        if ("学会做新菜" in profile.goals && difficulty != null && difficulty <= 2) points += 2
        if ("尝试不同风味" in profile.goals && region !in listOf("中式", NO_CONTENT)) points += 2
        val spicy = ingredients.any { it.name.containsAny(listOf("辣椒", "小米椒", "干辣椒", "辣酱", "豆瓣酱")) }
        if (profile.spice == "不吃辣" && spicy) points -= 12
        if (profile.spice in listOf("中辣", "重辣") && spicy) points += 3
        val tasteTerms = mapOf(
            "清淡" to listOf("清蒸", "水煮", "清汤", "凉拌"),
            "鲜香" to listOf("鲜", "菌菇", "香菇"),
            "酸甜" to listOf("糖醋", "酸甜", "番茄", "柠檬"),
            "浓郁" to listOf("红烧", "咖喱", "奶油", "浓"),
        )
        if (profile.tastes.any { text.containsAny(tasteTerms[it].orEmpty()) }) points += 3
        return points
    }
    // Score each recipe once rather than repeating ingredient/text matching in every comparison.
    return filter { it.matchesFoodExclusions(profile) }
        .map { it to it.score() }
        .sortedByDescending { it.second }
        .take(6)
        .map { it.first }
}
