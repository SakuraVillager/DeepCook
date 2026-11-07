import json
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import apply_step_timing as applier


def catalogue():
    return [
        {
            'id': 'r1',
            'title': '红烧肉',
            'steps': [
                {'id': 'r1-s1', 'order': 1, 'title': '切', 'text': '切块。'},
                {'id': 'r1-s2', 'order': 2, 'title': '炖', 'text': '炖四十分钟。'},
            ],
        },
    ]


def write_files(folder, items):
    recipes_path = Path(folder) / 'recipes.json'
    proposal_path = Path(folder) / 'step_timing.json'
    recipes_path.write_text(json.dumps(catalogue(), ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    proposal_path.write_text(json.dumps({'items': items}, ensure_ascii=False), encoding='utf-8')
    return recipes_path, proposal_path


class MergeTest(unittest.TestCase):
    def test_valid_values_are_written(self):
        recipes = catalogue()
        summary = applier.merge(
            recipes,
            {'r1-s1': {'minutes': 3, 'kind': 'ACTIVE'}, 'r1-s2': {'minutes': 40, 'kind': 'PASSIVE'}},
        )
        self.assertEqual(2, summary['applied'])
        self.assertEqual(2, summary['changed'])
        self.assertEqual(0, summary['missing'])
        self.assertEqual(40, recipes[0]['steps'][1]['minutes'])
        self.assertEqual('PASSIVE', recipes[0]['steps'][1]['kind'])

    def test_invalid_and_unknown_values_are_skipped(self):
        recipes = catalogue()
        summary = applier.merge(
            recipes,
            {
                'r1-s1': {'minutes': 300, 'kind': 'ACTIVE'},
                'r1-s2': {'minutes': 10, 'kind': 'WAIT'},
                'missing-step': {'minutes': 5, 'kind': 'ACTIVE'},
            },
        )
        self.assertEqual(0, summary['applied'])
        # Both real steps had a proposal (invalid ones are counted separately, not "missing").
        self.assertEqual(0, summary['missing'])
        self.assertEqual(['missing-step'], summary['unknown'])
        self.assertEqual(2, len(summary['invalid']))
        self.assertNotIn('minutes', recipes[0]['steps'][0])

    def test_steps_without_a_proposal_are_counted_as_missing(self):
        summary = applier.merge(catalogue(), {'r1-s1': {'minutes': 3, 'kind': 'ACTIVE'}})
        self.assertEqual(1, summary['missing'])

    def test_nothing_but_timing_changes(self):
        recipes = catalogue()
        original = json.loads(json.dumps(recipes))
        applier.merge(recipes, {'r1-s1': {'minutes': 3, 'kind': 'ACTIVE'}})
        stripped = json.loads(json.dumps(recipes))
        stripped[0]['steps'][0].pop('minutes')
        stripped[0]['steps'][0].pop('kind')
        self.assertEqual(original, stripped)

    def test_merging_twice_is_idempotent(self):
        recipes = catalogue()
        items = {'r1-s1': {'minutes': 3, 'kind': 'ACTIVE'}}
        applier.merge(recipes, items)
        summary = applier.merge(recipes, items)
        self.assertEqual(0, summary['changed'])

    def test_apply_writes_a_readable_file_and_a_second_run_keeps_the_bytes(self):
        with tempfile.TemporaryDirectory() as folder:
            recipes_path, proposal_path = write_files(folder, {'r1-s1': {'minutes': 3, 'kind': 'ACTIVE'}})
            argv = ['--recipes', str(recipes_path), '--proposal', str(proposal_path), '--apply']
            self.assertEqual(0, applier.main(argv))
            loaded = json.loads(recipes_path.read_text(encoding='utf-8'))
            self.assertEqual(3, loaded[0]['steps'][0]['minutes'])
            before = recipes_path.read_bytes()
            self.assertEqual(0, applier.main(argv))
            self.assertEqual(before, recipes_path.read_bytes())

    def test_require_complete_refuses_a_partial_proposal(self):
        with tempfile.TemporaryDirectory() as folder:
            recipes_path, proposal_path = write_files(folder, {'r1-s1': {'minutes': 3, 'kind': 'ACTIVE'}})
            argv = ['--recipes', str(recipes_path), '--proposal', str(proposal_path), '--apply', '--require-complete']
            self.assertEqual(1, applier.main(argv))
            loaded = json.loads(recipes_path.read_text(encoding='utf-8'))
            self.assertNotIn('minutes', loaded[0]['steps'][0])


if __name__ == '__main__':
    unittest.main()
