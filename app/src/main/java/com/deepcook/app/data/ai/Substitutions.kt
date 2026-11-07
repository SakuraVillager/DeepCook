package com.deepcook.app.data.ai

/**
 * Pantry swaps answered without the network.
 *
 * These are the questions a cook asks with wet hands: "没有蚝油用什么". A fixed table answers them
 * instantly and offline, and — more importantly — never invents a proportion. Anything not listed
 * falls through to the model, which sees the dish and its ingredient list.
 */
object Substitutions {
    internal val Table: List<Pair<String, String>> = listOf(
        "生抽" to "没有生抽可以用老抽减半再兑一点水，或者用味极鲜；咸度最后尝了再补。",
        "老抽" to "没有老抽可以用生抽加一点糖炒色，颜色会浅一些，味道差别不大。",
        "酱油" to "没有酱油可以用生抽加一点盐，或者用味极鲜代替。",
        "蚝油" to "没有蚝油可以用生抽一小勺加半勺糖代替，鲜味接近；也可以省略，出锅前补盐。",
        "料酒" to "没有料酒可以用黄酒或米酒，量一样；白酒要减半再兑点水。",
        "黄酒" to "用料酒或米酒代替，量可以一样。",
        "香醋" to "用陈醋或米醋代替，酸度接近，香味会淡一点。",
        "陈醋" to "用香醋或米醋代替，颜色会浅一点。",
        "白醋" to "用米醋代替；实在没有，柠檬汁也行，要多放一点。",
        "米醋" to "用白醋兑一点水，或者用陈醋减量。",
        "醋" to "没有醋可以用柠檬汁代替；也可以先省略，出锅前尝味补一点。",
        "冰糖" to "用白糖代替，甜度差不多，炒糖色时火要更小、时间更短。",
        "白糖" to "用冰糖敲碎代替，或者用蜂蜜但量减三分之一。",
        "砂糖" to "用白糖或冰糖代替，甜度按一样算。",
        "红糖" to "用白糖加一点老抽代替，颜色和焦香会差一些。",
        "蜂蜜" to "用白糖加一点水代替，甜度接近，只是少了香味。",
        "淀粉" to "用玉米淀粉或红薯淀粉代替；都没有就用面粉，量要减半。",
        "生粉" to "用玉米淀粉代替，勾芡效果一样。",
        "玉米淀粉" to "用土豆淀粉或生粉代替，勾芡效果一样。",
        "面粉" to "低筋可以用中筋加两成玉米淀粉；高筋不好替代，按原方做最稳。",
        "豆瓣酱" to "用豆豉剁碎加辣椒面代替，或者用韩式辣酱减量再加一点盐。",
        "豆豉" to "用豆瓣酱减量代替，咸度更高，盐要少放。",
        "甜面酱" to "用黄豆酱加一点糖代替，或者用蚝油加糖。",
        "黄豆酱" to "用甜面酱或豆瓣酱减量代替。",
        "番茄酱" to "用番茄沙司代替，或者番茄炒软加一点糖。",
        "花椒" to "用花椒粉减半代替；只有麻油的话滴一两滴提麻。",
        "八角" to "用五香粉少量代替；没有也可以省略，味道会单薄一点。",
        "桂皮" to "用肉桂粉少量代替；没有可以省略。",
        "香叶" to "没有可以省略，不影响成败。",
        "五香粉" to "用八角、桂皮各少量代替，或者省略。",
        "孜然" to "用孜然粉同量代替，香味出得快，别炒糊。",
        "咖喱" to "用咖喱粉加一点面粉炒香代替。",
        "鸡精" to "用味精少量代替，或者用一点糖提鲜。",
        "味精" to "用鸡精少量代替；介意的话省略，靠盐和糖提味。",
        "鱼露" to "用生抽加一点糖代替，咸度更高，盐要少放。",
        "淡奶油" to "用牛奶加黄油（约三比一）小火收浓，或者用椰浆。",
        "黄油" to "用等量植物油代替，奶香会少一些。",
        "牛奶" to "用奶粉冲开，或者用豆奶、清水加一点炼乳。",
        "椰浆" to "用牛奶加一点椰蓉代替，或者用淡奶油。",
        "香油" to "用一点熟植物油代替，香味会淡。",
        "芝麻油" to "用一点熟植物油代替，香味会淡。",
        "香菜" to "不爱吃可以不放，或者用葱花代替。",
        "小米椒" to "用干辣椒或辣椒面减量代替，辣度不同，先少放。",
        "干辣椒" to "用辣椒面或小米椒代替，辣度更冲，先少放。",
        "啤酒" to "用清水加一点料酒代替。",
        "白酒" to "用料酒代替，量减半。",
    )

    /** The most specific local answer for [ingredient], or null when the model should handle it. */
    fun find(ingredient: String): String? {
        val name = ingredient.trim()
        if (name.isEmpty()) return null
        return Table.filter { (key, _) -> key in name }
            .maxByOrNull { (key, _) -> key.length }
            ?.second
    }
}
