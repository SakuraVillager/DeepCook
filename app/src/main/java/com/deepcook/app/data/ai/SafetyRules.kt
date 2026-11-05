package com.deepcook.app.data.ai

import com.deepcook.app.core.model.Recipe

/**
 * Answers that must not depend on the model at all.
 *
 * Food-safety questions get one conservative answer (the user asked for "不建议吃" and nothing
 * softer). Allergy questions are resolved against this recipe's own ingredient list, because a
 * hallucinated "no shrimp in it" is the one mistake a cooking app cannot afford.
 */
object SafetyRules {
    const val NOT_RECOMMENDED = "不建议吃。"

    private val FoodSafety = listOf(
        "熟了吗", "熟了没", "熟没熟", "能不能吃", "能吃吗", "能吃么", "坏了", "变质", "馊",
        "食物中毒", "拉肚子", "孕妇", "孩子能", "宝宝能", "发霉",
    )

    private val AllergyWords = listOf("过敏", "忌口")

    private val AvoidAliases = mapOf(
        "辣" to listOf("辣椒", "小米椒", "豆瓣酱", "辣酱", "剁椒", "泡椒", "花椒", "辣油", "干辣椒"),
        "香菜" to listOf("香菜", "芫荽"),
        "葱" to listOf("葱", "小葱", "大葱", "香葱"),
        "姜" to listOf("姜", "生姜", "老姜", "姜片"),
        "蒜" to listOf("蒜", "大蒜", "蒜末", "蒜瓣"),
        "海鲜" to listOf("虾", "蟹", "贝", "蛤", "牡蛎", "生蚝", "鱿鱼", "章鱼", "鲍鱼", "海参", "鱼露", "虾皮"),
        "花生" to listOf("花生", "花生米", "花生碎"),
        "牛肉" to listOf("牛肉", "牛腩", "牛排", "肥牛", "牛腱"),
        "羊肉" to listOf("羊肉", "羊排", "羊腿", "羊蝎子"),
        "内脏" to listOf("猪肝", "鸡肝", "鹅肝", "猪肠", "肥肠", "猪肚", "毛肚", "猪心", "鸡心", "猪腰"),
    )

    private val NamedAllergens = mapOf(
        "海鲜" to AvoidAliases.getValue("海鲜"),
        "虾" to listOf("虾", "虾仁", "虾米", "虾皮"),
        "蟹" to listOf("蟹", "蟹肉", "蟹柳"),
        "鱼" to listOf("鱼", "鱼露", "鱼丸", "鱼片"),
        "花生" to listOf("花生", "花生米", "花生碎"),
        "坚果" to listOf("花生", "核桃", "腰果", "杏仁", "榛子", "开心果"),
        "牛奶" to listOf("牛奶", "奶油", "黄油", "芝士", "奶酪", "炼乳"),
        "鸡蛋" to listOf("鸡蛋", "蛋清", "蛋黄", "蛋液"),
        "麸质" to listOf("面粉", "面条", "面包", "馒头"),
        "大豆" to listOf("黄豆", "豆腐", "豆浆", "酱油", "豆瓣酱"),
        "芝麻" to listOf("芝麻"),
        "芒果" to listOf("芒果"),
    )

    fun answer(question: String, recipe: Recipe, avoids: Set<String>): String? {
        val text = question.trim()
        if (text.isEmpty()) return null
        if (AllergyWords.any { text.contains(it) }) return allergyAnswer(text, recipe, avoids)
        if (FoodSafety.any { text.contains(it) }) {
            return NOT_RECOMMENDED + "熟没熟、坏没坏我没法替你判断，宁可再加热到全熟。"
        }
        return null
    }

    private fun allergyAnswer(question: String, recipe: Recipe, avoids: Set<String>): String {
        val explicit = linkedSetOf<String>()
        NamedAllergens.keys.filter { question.contains(it) }.forEach(explicit::add)
        avoids.filter { question.contains(it) }.forEach(explicit::add)
        recipe.ingredients.map { it.name }.filter { question.contains(it) }.forEach(explicit::add)

        val terms = if (explicit.isNotEmpty()) {
            explicit.flatMap(::expand).toSet()
        } else {
            avoids.flatMap(::expand).toSet()
        }
        if (terms.isEmpty()) {
            val names = recipe.ingredients.take(8).joinToString("、") { it.name }
            return "这道菜的材料是：$names。请对照你的过敏原核对。"
        }
        val hits = recipe.ingredients.filter { ingredient -> terms.any { it in ingredient.name } }
        return if (hits.isEmpty()) {
            "这道菜的材料里没有${terms.joinToString("、")}；你额外加的料请自行核对。"
        } else {
            NOT_RECOMMENDED + "材料里有${hits.joinToString("、") { it.name }}。"
        }
    }

    private fun expand(term: String): List<String> = AvoidAliases[term] ?: NamedAllergens[term] ?: listOf(term)
}
