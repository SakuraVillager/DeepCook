// Generated from DeepCook-Web/src/data/food-rules.json by tools/sync-food-rules.py.
package com.deepcook.app.core.model

internal object FoodRules {
    val aliases = mapOf(
        "牛奶及乳制品" to listOf("奶", "乳", "黄油", "芝士", "奶酪"),
        "鸡蛋" to listOf("蛋"),
        "花生" to listOf("花生"),
        "坚果" to listOf("核桃", "杏仁", "腰果", "榛子", "松子", "开心果", "碧根果", "坚果"),
        "大豆" to listOf("大豆", "黄豆", "豆腐", "豆浆", "豆皮", "腐竹", "豆瓣", "豆豉", "酱油", "生抽", "老抽", "味噌"),
        "小麦" to listOf("小麦", "面粉", "面包", "面条", "意面", "意大利面", "通心粉", "酱油", "生抽", "老抽", "面筋"),
        "鱼类" to listOf("鱼", "鳝", "鳗", "鱼露"),
        "虾蟹" to listOf("虾", "蟹", "虾酱"),
        "贝类" to listOf("贝", "蛤", "蚝", "牡蛎", "蛏", "蚌", "贻", "鲍"),
        "芝麻" to listOf("芝麻", "香油", "麻油"),
        "蘑菇" to listOf("蘑菇", "香菇", "口蘑", "金针菇", "杏鲍菇", "菌菇"),
        "动物内脏" to listOf("肝", "腰子", "猪肚", "牛肚", "肥肠", "鸡胗", "鸭胗", "内脏"),
        "内脏" to listOf("肝", "腰子", "猪肚", "牛肚", "肥肠", "鸡胗", "鸭胗", "内脏"),
        "牛肉" to listOf("牛肉", "牛腩", "牛排", "肥牛", "牛筋"),
        "羊肉" to listOf("羊肉", "羊排", "羊腿"),
        "猪肉" to listOf("猪肉", "五花", "里脊", "排骨", "培根", "火腿", "猪油", "肉末", "肉馅"),
        "海鲜" to listOf("鱼", "虾", "蟹", "贝", "蛤", "蚝", "蛏", "蚌", "贻", "鲍", "鳗", "鳝"),
        "辣" to listOf("辣椒", "小米椒", "朝天椒", "辣酱", "豆瓣酱", "辣油")
    )
    val cuisines = mapOf(
        "中式家常" to listOf("中式"),
        "川湘风味" to listOf("川", "湘", "麻婆", "宫保", "回锅", "辣子"),
        "粤式风味" to listOf("粤", "广式", "白切", "清蒸", "煲"),
        "江浙风味" to listOf("江浙", "杭", "浙", "苏式", "糖醋"),
        "日韩料理" to listOf("日式", "韩式"),
        "东南亚菜" to listOf("东南亚"),
        "西式料理" to listOf("美式", "欧式"),
        "其他风味" to listOf("其他")
    )
    val tastes = mapOf(
        "清淡" to listOf("清蒸", "水煮", "清汤", "凉拌"),
        "鲜香" to listOf("鲜", "菌菇", "香菇"),
        "酸甜" to listOf("糖醋", "酸甜", "番茄", "柠檬"),
        "浓郁" to listOf("红烧", "咖喱", "奶油", "浓"),
        "甜" to listOf("甜", "糖"),
        "咸" to listOf("咸"),
        "酸" to listOf("酸", "醋", "柠檬"),
        "麻" to listOf("麻", "花椒"),
        "辣" to listOf("辣", "小米椒"),
        "鲜" to listOf("鲜"),
        "油炸" to listOf("炸"),
        "汤羹" to listOf("汤", "羹"),
        "下饭" to listOf("下饭")
    )
    val animal = listOf("肉", "猪", "牛腩", "牛排", "肥牛", "羊", "鸡", "鸭", "鹅", "鱼", "虾", "蟹", "贝", "蚝", "蛤", "蛏", "鲍", "排骨", "培根", "火腿", "明胶", "鱼露", "虾酱")
    val nonVegan = listOf("蛋", "奶", "乳", "黄油", "芝士", "奶酪", "蜂蜜")
    val plantNames = listOf("鸡腿菇", "杏鲍菇", "羊肚菌", "猴头菇", "牛肝菌", "鱼腥草", "鸡枞菌", "素鸡", "植物奶", "椰奶", "椰乳", "燕麦奶", "豆奶", "花生奶")
    val weights = mapOf(
        "featured" to 1,
        "cuisine" to 6,
        "taste" to 3,
        "time" to 4,
        "beginner" to 4,
        "quick" to 4,
        "learn" to 2,
        "explore" to 2,
        "spicy" to 3
    )
    val notice = "材料信息可能不完整；请核对实际食材、调料及包装配料，未命中不代表不含过敏原。"
}
