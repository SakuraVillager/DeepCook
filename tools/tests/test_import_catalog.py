import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import import_catalog as importer


class ImportParserTest(unittest.TestCase):
    def test_math_is_not_removed_as_markdown_emphasis(self):
        self.assertEqual('肉 1.5cm*1.5cm，酱 10ml*份数', importer.plain('肉 `1.5cm*1.5cm`，酱 **10ml*份数**'))

    def test_colon_quantities_and_missing_quantities(self):
        self.assertEqual([('盐', '2 克'), ('水', '无'), ('椰子水', '50ml')], importer.ingredient_rows(['- 盐：2 克', '- 水', '- 50ml 椰子水']))

    def test_nested_bullets_stay_in_the_parent_step(self):
        self.assertEqual(['加入：\n- 生抽 10ml\n- 老抽 5ml', '收汁'], importer.list_items(['1. 加入：', '  - 生抽 10ml', '  - 老抽 5ml', '2. 收汁']))

    def test_source_links_are_data_and_images_do_not_create_steps(self):
        self.assertEqual(['查看糖色'], importer.list_items(['1. 查看[糖色](./sugar.md)', '![成品](./food.jpg)']))

    def test_total_time_is_explicit_not_inferred_from_step_times(self):
        self.assertEqual(30, importer.total_minutes('从备料到出锅大约只需三十分钟。'))
        self.assertEqual(90, importer.total_minutes('预计制作时长约 1 小时 30 分钟。'))
        self.assertEqual(90, importer.total_minutes('全程约一小时半。'))
        self.assertIsNone(importer.total_minutes('全程制作（含腌制）约需 2–2.5 小时。'))
        self.assertIsNone(importer.total_minutes('小火煮 20 分钟，随后再炒 10 分钟。'))

    def test_recipe_formulas_remain_visible_in_amount_column(self):
        self.assertEqual([('生抽', '份数 * 3ml')], importer.ingredient_rows(['- 生抽 = 份数 * 3ml']))

    def test_chinese_written_quantities_are_not_lost(self):
        self.assertEqual([('鲈鱼', '一条'), ('香葱', '三根')], importer.ingredient_rows(['- 鲈鱼 一条', '- 香葱 三根']))


if __name__ == '__main__':
    unittest.main()
