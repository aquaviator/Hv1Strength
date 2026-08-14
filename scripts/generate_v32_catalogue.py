"""Deterministically build the curated V32 Strength exercise catalogue."""
import hashlib, json, pathlib, re

CATALOGUE = pathlib.Path(__file__).parents[1] / "app/src/main/assets/strength-exercise-catalogue.json"

# id|name|category|movement|equipment|primary|secondary
SPECS = """
floor_press|Barbell Floor Press|Chest|horizontal push|barbell|chest,triceps|front deltoids
db_floor_press|Dumbbell Floor Press|Chest|horizontal push|dumbbell|chest,triceps|front deltoids
incline_cable_press|Incline Cable Press|Chest|horizontal push|cable,bench|chest|triceps,front deltoids
decline_db_press|Decline Dumbbell Press|Chest|horizontal push|dumbbell,bench|chest|triceps
single_arm_db_floor_press|Single-Arm Dumbbell Floor Press|Chest|horizontal push|dumbbell|chest,triceps|core
band_chest_press|Resistance-Band Chest Press|Chest|horizontal push|resistance band|chest|triceps
kneeling_push_up|Kneeling Push Up|Chest|horizontal push|bodyweight|chest|triceps
incline_push_up|Incline Push Up|Chest|horizontal push|bodyweight,bench|chest|triceps
decline_push_up|Decline Push Up|Chest|horizontal push|bodyweight,bench|chest|triceps,shoulders
ring_push_up|Ring Push Up|Chest|horizontal push|gymnastic rings|chest|triceps,core
archer_push_up|Archer Push Up|Chest|horizontal push|bodyweight|chest,triceps|core
medicine_ball_chest_pass|Medicine-Ball Chest Pass|Chest|horizontal push|medicine ball|chest,triceps|shoulders
wide_grip_pulldown|Wide-Grip Lat Pulldown|Back|vertical pull|cable|lats|biceps
underhand_pulldown|Underhand Lat Pulldown|Back|vertical pull|cable|lats,biceps|upper back
band_pulldown|Resistance-Band Pulldown|Back|vertical pull|resistance band|lats|biceps
half_kneeling_pulldown|Half-Kneeling Single-Arm Pulldown|Back|vertical pull|cable|lats|biceps,core
seal_row|Barbell Seal Row|Back|horizontal pull|barbell,bench|upper back,lats|biceps
pendlay_row|Pendlay Row|Back|horizontal pull|barbell|upper back,lats|biceps
wide_grip_cable_row|Wide-Grip Cable Row|Back|horizontal pull|cable|upper back|lats,biceps
single_arm_cable_row|Single-Arm Cable Row|Back|horizontal pull|cable|lats,upper back|biceps,core
band_row|Resistance-Band Row|Back|horizontal pull|resistance band|upper back,lats|biceps
suspension_row|Suspension-Trainer Row|Back|horizontal pull|suspension trainer|upper back,lats|biceps
ring_row|Ring Row|Back|horizontal pull|gymnastic rings|upper back,lats|biceps
scapular_pull_up|Scapular Pull Up|Back|scapular movement|pull-up bar|lats,lower traps|forearms
prone_y_raise|Prone Y Raise|Back|scapular movement|dumbbell,bench|lower traps|rear deltoids
cable_shrug|Cable Shrug|Back|scapular movement|cable|traps|forearms
smith_shrug|Smith-Machine Shrug|Back|scapular movement|smith machine|traps|forearms
single_arm_db_shrug|Single-Arm Dumbbell Shrug|Back|scapular movement|dumbbell|traps|core,forearms
z_press|Barbell Z Press|Shoulders|vertical push|barbell|shoulders|triceps,core
standing_db_press|Standing Dumbbell Shoulder Press|Shoulders|vertical push|dumbbell|shoulders|triceps,core
single_arm_db_press|Single-Arm Dumbbell Shoulder Press|Shoulders|vertical push|dumbbell|shoulders|triceps,core
half_kneeling_landmine_press|Half-Kneeling Landmine Press|Shoulders|vertical push|landmine|front deltoids|triceps,core
band_overhead_press|Resistance-Band Overhead Press|Shoulders|vertical push|resistance band|shoulders|triceps
behind_neck_band_pull_apart|Behind-the-Back Band Pull-Apart|Shoulders|shoulder isolation|resistance band|rear deltoids|upper back
lean_away_lateral_raise|Lean-Away Cable Lateral Raise|Shoulders|shoulder isolation|cable|side deltoids|core
lying_cable_lateral_raise|Lying Cable Lateral Raise|Shoulders|shoulder isolation|cable,bench|side deltoids|
machine_rear_delt_fly|Machine Rear-Delt Fly|Shoulders|shoulder isolation|selectorized machine|rear deltoids|upper back
incline_rear_delt_raise|Incline Rear-Delt Raise|Shoulders|shoulder isolation|dumbbell,bench|rear deltoids|upper back
cable_external_rotation|Cable External Rotation|Shoulders|shoulder rotation|cable|rotator cuff|rear deltoids
band_external_rotation|Resistance-Band External Rotation|Shoulders|shoulder rotation|resistance band|rotator cuff|rear deltoids
bayesian_curl|Bayesian Cable Curl|Arms|elbow flexion|cable|biceps|
spider_curl|Dumbbell Spider Curl|Arms|elbow flexion|dumbbell,bench|biceps|forearms
machine_preacher_curl|Machine Preacher Curl|Arms|elbow flexion|selectorized machine|biceps|
single_arm_cable_curl|Single-Arm Cable Curl|Arms|elbow flexion|cable|biceps|forearms
cross_body_hammer_curl|Cross-Body Hammer Curl|Arms|elbow flexion|dumbbell|biceps,forearms|
z_ottman_curl|Zottman Curl|Arms|elbow flexion|dumbbell|biceps,forearms|
band_curl|Resistance-Band Curl|Arms|elbow flexion|resistance band|biceps|
lying_db_triceps_extension|Lying Dumbbell Triceps Extension|Arms|elbow extension|dumbbell,bench|triceps|
single_arm_overhead_triceps_extension|Single-Arm Overhead Triceps Extension|Arms|elbow extension|dumbbell|triceps|
cross_body_cable_extension|Cross-Body Cable Triceps Extension|Arms|elbow extension|cable|triceps|
reverse_grip_pushdown|Reverse-Grip Triceps Pushdown|Arms|elbow extension|cable|triceps|
band_pushdown|Resistance-Band Triceps Pushdown|Arms|elbow extension|resistance band|triceps|
bench_dip|Bench Dip|Arms|elbow extension|bench,bodyweight|triceps|chest
single_arm_wrist_curl|Single-Arm Wrist Curl|Arms|grip|dumbbell|forearms|
hammer_wrist_rotation|Dumbbell Wrist Rotation|Arms|grip|dumbbell|forearms,grip|
towel_hang|Towel Hang|Arms|grip|pull-up bar,towel|forearms,grip|lats
farmers_hold|Farmer's Hold|Arms|grip|dumbbell|forearms,grip|traps
overhead_squat|Overhead Squat|Legs|squat|barbell|quadriceps,glutes|shoulders,core
landmine_squat|Landmine Squat|Legs|squat|landmine|quadriceps,glutes|core
double_kb_front_squat|Double-Kettlebell Front Squat|Legs|squat|kettlebell|quadriceps,glutes|core
single_leg_box_squat|Single-Leg Box Squat|Legs|squat|box,bodyweight|quadriceps,glutes|core
assisted_squat|Suspension-Assisted Squat|Legs|squat|suspension trainer|quadriceps,glutes|core
cyclist_squat|Cyclist Squat|Legs|squat|weight plate,dumbbell|quadriceps|glutes
smith_split_squat|Smith-Machine Split Squat|Legs|lunge|smith machine|quadriceps,glutes|hamstrings
front_foot_elevated_split_squat|Front-Foot-Elevated Split Squat|Legs|lunge|dumbbell,weight plate|quadriceps,glutes|hamstrings
curtsy_lunge|Curtsy Lunge|Legs|lunge|dumbbell|glutes,quadriceps|adductors
lateral_lunge|Lateral Lunge|Legs|lunge|dumbbell|adductors,glutes|quadriceps
deficit_reverse_lunge|Deficit Reverse Lunge|Legs|lunge|dumbbell,weight plate|quadriceps,glutes|hamstrings
walking_barbell_lunge|Barbell Walking Lunge|Legs|lunge|barbell|quadriceps,glutes|hamstrings
smith_rdl|Smith-Machine Romanian Deadlift|Legs|hinge|smith machine|hamstrings,glutes|back
dumbbell_rdl|Dumbbell Romanian Deadlift|Legs|hinge|dumbbell|hamstrings,glutes|back
kettlebell_rdl|Kettlebell Romanian Deadlift|Legs|hinge|kettlebell|hamstrings,glutes|back
kickstand_rdl|Kickstand Romanian Deadlift|Legs|hinge|dumbbell|hamstrings,glutes|core
deficit_deadlift|Deficit Barbell Deadlift|Legs|hinge|barbell,weight plate|glutes,hamstrings|back
block_pull|Barbell Block Pull|Legs|hinge|barbell,blocks|glutes,back|hamstrings
trap_bar_rdl|Trap-Bar Romanian Deadlift|Legs|hinge|trap bar|hamstrings,glutes|back
barbell_glute_bridge|Barbell Glute Bridge|Legs|hip extension|barbell|glutes|hamstrings
single_leg_hip_thrust|Single-Leg Hip Thrust|Legs|hip extension|bench,bodyweight|glutes|hamstrings
machine_hip_thrust|Machine Hip Thrust|Legs|hip extension|plate-loaded machine|glutes|hamstrings
cable_glute_kickback|Cable Glute Kickback|Legs|hip extension|cable|glutes|hamstrings
band_lateral_walk|Resistance-Band Lateral Walk|Legs|hip abduction|resistance band|glutes|quadriceps
side_lying_hip_abduction|Side-Lying Hip Abduction|Legs|hip abduction|bodyweight|glutes|
copenhagen_plank|Copenhagen Plank|Legs|hip adduction|bench,bodyweight|adductors|core
nordic_hamstring_curl|Nordic Hamstring Curl|Legs|knee flexion|bodyweight|hamstrings|calves
swiss_ball_leg_curl|Swiss-Ball Leg Curl|Legs|knee flexion|stability ball|hamstrings|glutes
slider_leg_curl|Slider Leg Curl|Legs|knee flexion|sliders|hamstrings|glutes
reverse_nordic|Reverse Nordic Curl|Legs|knee extension|bodyweight|quadriceps|core
single_leg_extension|Single-Leg Extension|Legs|knee extension|selectorized machine|quadriceps|
donkey_calf_raise|Donkey Calf Raise|Legs|plantar flexion|bodyweight|calves|
tibialis_raise|Tibialis Raise|Legs|dorsiflexion|bodyweight|tibialis anterior|
standing_single_leg_tibialis_raise|Single-Leg Tibialis Raise|Legs|dorsiflexion|bodyweight|tibialis anterior|
hollow_body_hold|Hollow-Body Hold|Core|anti-extension|bodyweight|core|hip flexors
body_saw|Body Saw|Core|anti-extension|suspension trainer|core|shoulders
stability_ball_rollout|Stability-Ball Rollout|Core|anti-extension|stability ball|core|lats
long_lever_plank|Long-Lever Plank|Core|anti-extension|bodyweight|core|shoulders
half_kneeling_pallof_press|Half-Kneeling Pallof Press|Core|anti-rotation|cable|core,obliques|glutes
band_pallof_press|Resistance-Band Pallof Press|Core|anti-rotation|resistance band|core,obliques|
pallof_press_walkout|Pallof Press Walkout|Core|anti-rotation|cable|core,obliques|glutes
single_arm_front_rack_carry|Single-Arm Front-Rack Carry|Core|anti-lateral flexion|kettlebell|core,obliques|forearms
waiter_carry|Waiter's Carry|Core|anti-lateral flexion|kettlebell|core,shoulders|forearms
side_plank_row|Side-Plank Cable Row|Core|anti-rotation|cable|obliques,upper back|lats
wood_chop|Cable Wood Chop|Core|rotation|cable|obliques,core|shoulders
medicine_ball_rotation_throw|Medicine-Ball Rotational Throw|Core|rotation|medicine ball|obliques,core|shoulders
stability_ball_crunch|Stability-Ball Crunch|Core|trunk flexion|stability ball|core|
decline_crunch|Decline-Bench Crunch|Core|trunk flexion|bench|core|hip flexors
roman_chair_knee_raise|Roman-Chair Knee Raise|Core|trunk flexion|captain's chair|core|hip flexors
superman_hold|Superman Hold|Core|trunk extension|bodyweight|lower back|glutes
reverse_hyperextension|Reverse Hyperextension|Core|trunk extension|reverse hyper machine|glutes,lower back|hamstrings
bear_crawl|Bear Crawl|Full Body|locomotion|bodyweight|core,shoulders|quadriceps
crab_walk|Crab Walk|Full Body|locomotion|bodyweight|shoulders,glutes|core
sandbag_bear_hug_carry|Sandbag Bear-Hug Carry|Full Body|loaded carry|sandbag|core,legs|arms
trap_bar_carry|Trap-Bar Carry|Full Body|loaded carry|trap bar|forearms,core|traps,legs
overhead_plate_carry|Overhead Plate Carry|Full Body|loaded carry|weight plate|shoulders,core|triceps
sled_drag|Backward Sled Drag|Full Body|sled|sled|quadriceps,calves|glutes
sled_pull|Rope Sled Pull|Full Body|sled|sled,rope|back,arms|legs
kettlebell_clean|Kettlebell Clean|Full Body|power|kettlebell|glutes,hamstrings|shoulders
single_arm_kb_swing|Single-Arm Kettlebell Swing|Full Body|hinge|kettlebell|glutes,hamstrings|core
medicine_ball_slam|Medicine-Ball Slam|Full Body|power|medicine ball|lats,core|shoulders
box_jump|Box Jump|Full Body|plyometric|box|quadriceps,glutes|calves
broad_jump|Standing Broad Jump|Full Body|plyometric|bodyweight|glutes,quadriceps|calves
skater_jump|Skater Jump|Full Body|plyometric|bodyweight|glutes,quadriceps|calves
jump_rope|Jump Rope|Cardio|conditioning|jump rope|calves|shoulders
elliptical|Elliptical Trainer|Cardio|cardio|cardio equipment|legs|arms
air_bike|Air Bike|Cardio|cardio|cardio equipment|legs,arms|core
ski_erg|Ski Ergometer|Cardio|cardio|cardio equipment|lats,arms|core,legs
incline_treadmill_walk|Incline Treadmill Walk|Cardio|cardio|treadmill|legs,glutes|calves
outdoor_run|Outdoor Run|Cardio|cardio|bodyweight|legs|calves
outdoor_walk|Outdoor Walk|Cardio|cardio|bodyweight|legs|calves
pool_swim|Pool Swim|Cardio|cardio|swimming pool|full body|shoulders
mountain_climber|Mountain Climber|Cardio|conditioning|bodyweight|core,shoulders|legs
burpee|Burpee|Cardio|conditioning|bodyweight|full body|core
high_knees|High Knees|Cardio|conditioning|bodyweight|legs|core
ankle_rock|Ankle Rock|Mobility|mobility|bodyweight|calves|ankles
worlds_greatest_stretch|World's Greatest Stretch|Mobility|mobility|bodyweight|hips|upper back
cat_cow|Cat-Cow|Mobility|mobility|bodyweight|spine|core
thoracic_rotation|Quadruped Thoracic Rotation|Mobility|rotation|bodyweight|upper back|shoulders
wall_slide|Wall Slide|Mobility|scapular movement|bodyweight|shoulders,upper back|
band_dislocate|Resistance-Band Shoulder Pass-Through|Mobility|mobility|resistance band|shoulders|chest
hip_airplane|Hip Airplane|Mobility|mobility|bodyweight|glutes,hips|core
adductor_rockback|Adductor Rockback|Mobility|mobility|bodyweight|adductors|hips
""".strip()

def aliases(name):
    values=[]
    if "Single-Arm" in name: values.append(name.replace("Single-Arm", "One-Arm"))
    if "Resistance-Band" in name: values.append(name.replace("Resistance-Band", "Band"))
    if "Romanian Deadlift" in name: values.append(name.replace("Romanian Deadlift", "RDL"))
    if "Kettlebell" in name: values.append(name.replace("Kettlebell", "KB"))
    return values[:2]

def detail(e):
    equipment = ", ".join(e["equipment"])
    pattern = e["movementPattern"]
    e.update({
        "setup": f"Set up with {equipment}. Choose a stable position that lets you control the {pattern} pattern.",
        "steps": [f"Brace gently and begin the {pattern} with a controlled range.", "Move smoothly without bouncing or rushing.", "Return under control and reset before the next repetition."],
        "breathing": "Breathe in to prepare; breathe out through the effort while keeping a comfortable brace.",
        "cues": ["Use a controlled range", "Keep the working joints aligned", "Stop the set when technique changes"],
        "mistakes": ["Using momentum instead of control", "Choosing a load that shortens the intended range"],
        "safety": "Use a manageable load and a pain-free range. Stop if you feel sharp pain, dizziness, or unusual discomfort; seek qualified guidance when needed.",
    })

def inferred_pattern(e):
    name=e["name"].lower()
    if any(x in name for x in ("pulldown","pull up","pull-up","chin up")): return "vertical pull"
    if "row" in name: return "horizontal pull"
    if any(x in name for x in ("press","push up","push-up","fly","dip")): return "vertical push" if any(x in name for x in ("overhead","shoulder","arnold","landmine")) else "horizontal push"
    if any(x in name for x in ("deadlift","good morning","pull-through","swing")): return "hinge"
    if any(x in name for x in ("squat","leg press")): return "squat"
    if any(x in name for x in ("lunge","split squat","step up")): return "lunge"
    if "carry" in name: return "loaded carry"
    if e["category"] in ("Abs","Core"): return "core"
    if e["category"]=="Cardio": return "cardio"
    return e.get("movementPattern") or "strength"

root=json.loads(CATALOGUE.read_text(encoding="utf-8"))
existing={e["id"]:e for e in root["exercises"]}
for e in existing.values():
    e.setdefault("movementPattern", {"Chest":"horizontal push","Back":"horizontal pull","Shoulders":"vertical push","Arms":"elbow flexion or extension","Legs":"lower-body strength","Abs":"core","Core":"core","Full Body":"full-body","Cardio":"cardio"}.get(e["category"], "strength"))
    e["movementPattern"]=inferred_pattern(e)
    detail(e)

for line in SPECS.splitlines():
    exercise_id,name,category,pattern,equipment,primary,secondary=line.split("|")
    if exercise_id in existing:
        if existing[exercise_id]["name"] != name: raise SystemExit(f"semantic id collision: {exercise_id}")
        continue
    e={"id":exercise_id,"name":name,"aliases":aliases(name),"category":category,
       "primaryMuscles":primary.split(","),"secondaryMuscles":secondary.split(",") if secondary else [],
       "equipment":equipment.split(","),"type":"cardio" if category=="Cardio" else ("bodyweight" if equipment=="bodyweight" else "conditioning" if pattern in ("conditioning","plyometric","power","locomotion","loaded carry","sled") else "strength"),
       "capabilities":["duration","distance","rpe"] if category=="Cardio" else (["duration","rpe"] if category=="Mobility" or "hold" in exercise_id or "carry" in exercise_id else ["repetitions","load","rpe","tempo"]),
       "laterality":"unilateral" if any(x in exercise_id for x in ("single_","single_leg","side_","kickstand")) else "bilateral",
       "bodyweight":"bodyweight" in equipment.split(","),"active":True,"movementPattern":pattern}
    if e["bodyweight"]:
        e["capabilities"]=[c for c in e["capabilities"] if c!="load"]
        if "duration" not in e["capabilities"]: e["capabilities"].insert(1,"bodyweight")
    detail(e); existing[exercise_id]=e; root["exercises"].append(e)

# Deterministic, bounded related discovery from shared movement/muscle/equipment metadata.
for e in root["exercises"]:
    scored=[]
    for other in root["exercises"]:
        if other["id"]==e["id"]: continue
        score=4*(other["movementPattern"]==e["movementPattern"])+2*len(set(other["primaryMuscles"])&set(e["primaryMuscles"]))+len(set(other["equipment"])&set(e["equipment"]))
        if score: scored.append((-score, other["name"].lower(), other["id"]))
    e["relatedIds"]=[item[2] for item in sorted(scored)[:4]]

root["catalogueVersion"]="2026.08.32"
root["releasedAt"]="2026-08-14T00:00:00Z"
root["exerciseCount"]=len(root["exercises"])
payload=json.dumps(root["exercises"],ensure_ascii=False,separators=(",",":"))
root["payloadChecksum"]=hashlib.sha256(payload.encode()).hexdigest()
CATALOGUE.write_text(json.dumps(root,ensure_ascii=False,separators=(",",":")),encoding="utf-8")
print(root["exerciseCount"], root["payloadChecksum"])
