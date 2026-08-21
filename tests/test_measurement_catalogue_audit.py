import json, sys, tempfile, unittest
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parents[1]/"tools"))
from measurement_catalogue_audit import run

class MeasurementCatalogueAuditTest(unittest.TestCase):
    def test_full_candidate_is_deterministic_and_preserves_every_id(self):
        root=Path(__file__).resolve().parents[1]
        source=root/"build/v36-publication-readiness/release-exercises.json"
        if not source.exists(): self.skipTest("verified V36 release export unavailable")
        with tempfile.TemporaryDirectory() as a, tempfile.TemporaryDirectory() as b:
            first=run(source,Path(a)); second=run(source,Path(b))
            self.assertEqual(first,second)
            self.assertEqual(955,first["exerciseCount"])
            self.assertEqual(955,first["uniqueStableIds"])
            self.assertEqual((Path(a)/"measurement-profile-candidate.json").read_bytes(),(Path(b)/"measurement-profile-candidate.json").read_bytes())
            candidate=json.loads((Path(a)/"measurement-profile-candidate.json").read_text())
            treadmill=next(x for x in candidate["profiles"] if x["exerciseId"]=="treadmill_run")
            self.assertNotIn("external_load",treadmill["recordingMetrics"])
            self.assertNotIn("repetitions",treadmill["recordingMetrics"])
            self.assertIn("pace",treadmill["derivedMetrics"])

if __name__=="__main__": unittest.main()
