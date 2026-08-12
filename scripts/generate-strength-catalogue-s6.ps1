param([string]$Path = "$PSScriptRoot/../app/src/main/assets/strength-exercise-catalogue.json")
$document = Get-Content -Raw -LiteralPath $Path | ConvertFrom-Json
$specs = @'
decline_bench_press|Decline Bench Press|decline press|Chest|chest|triceps;front deltoids|barbell;bench|strength|bilateral|false|standard
dumbbell_bench_press|Dumbbell Bench Press|flat dumbbell press|Chest|chest|triceps;front deltoids|dumbbell;bench|strength|bilateral|false|standard
incline_bench_press|Incline Bench Press|incline barbell press|Chest|chest|triceps;front deltoids|barbell;bench|strength|bilateral|false|standard
machine_chest_press|Machine Chest Press|selectorized chest press|Chest|chest|triceps;front deltoids|selectorized machine|strength|bilateral|false|standard
plate_loaded_chest_press|Plate-Loaded Chest Press|hammer strength chest press|Chest|chest|triceps|plate-loaded machine|strength|bilateral|false|standard
cable_chest_fly|Cable Chest Fly|cable crossover|Chest|chest|front deltoids|cable|strength|bilateral|false|standard
pec_deck|Pec Deck|machine fly|Chest|chest||selectorized machine|strength|bilateral|false|standard
close_grip_push_up|Close-Grip Push Up|diamond push up|Chest|triceps;chest|shoulders|bodyweight|bodyweight|bilateral|true|body
dip|Parallel Bar Dip|chest dip;triceps dip|Chest|chest;triceps|front deltoids|dip bars|bodyweight|bilateral|true|body_assisted
single_arm_cable_press|Single-Arm Cable Chest Press|unilateral cable press|Chest|chest|triceps;core|cable|strength|unilateral|false|standard
chin_up|Chin Up|chinup;underhand pull up|Back|lats;biceps|upper back|pull-up bar|bodyweight|bilateral|true|body_assisted
assisted_pull_up|Assisted Pull Up|machine pull up|Back|lats|biceps;upper back|selectorized machine|strength|bilateral|true|assisted
neutral_grip_pulldown|Neutral-Grip Lat Pulldown|neutral pulldown|Back|lats|biceps|cable|strength|bilateral|false|standard
single_arm_pulldown|Single-Arm Lat Pulldown|unilateral pulldown|Back|lats|biceps|cable|strength|unilateral|false|standard
seated_cable_row|Seated Cable Row|low cable row|Back|upper back;lats|biceps|cable|strength|bilateral|false|standard
chest_supported_db_row|Chest-Supported Dumbbell Row|incline bench row|Back|upper back;lats|biceps|dumbbell;bench|strength|bilateral|false|standard
machine_row|Machine Row|selectorized row|Back|upper back;lats|biceps|selectorized machine|strength|bilateral|false|standard
single_arm_db_row|Single-Arm Dumbbell Row|one arm row|Back|lats;upper back|biceps|dumbbell;bench|strength|unilateral|false|standard
t_bar_row|T-Bar Row|chest supported t bar row|Back|upper back;lats|biceps|plate-loaded machine|strength|bilateral|false|standard
landmine_row|Landmine Row|meadows row|Back|upper back;lats|biceps;core|landmine|strength|bilateral|false|standard
inverted_row|Inverted Row|body row|Back|upper back;lats|biceps|suspension trainer|bodyweight|bilateral|true|body
straight_arm_pulldown|Straight-Arm Pulldown|cable pullover|Back|lats|triceps|cable|strength|bilateral|false|standard
barbell_shrug|Barbell Shrug|shrug|Back|traps|forearms|barbell|strength|bilateral|false|standard
back_extension|Back Extension|hyperextension|Back|lower back|glutes;hamstrings|back extension bench|strength|bilateral|true|body
good_morning|Barbell Good Morning|good morning|Back|hamstrings;lower back|glutes|barbell|strength|bilateral|false|standard
seated_db_shoulder_press|Seated Dumbbell Shoulder Press|dumbbell overhead press|Shoulders|front deltoids;side deltoids|triceps|dumbbell;bench|strength|bilateral|false|standard
machine_shoulder_press|Machine Shoulder Press|selectorized shoulder press|Shoulders|shoulders|triceps|selectorized machine|strength|bilateral|false|standard
arnold_press|Arnold Press|rotating dumbbell press|Shoulders|shoulders|triceps|dumbbell|strength|bilateral|false|standard
single_arm_landmine_press|Single-Arm Landmine Press|landmine shoulder press|Shoulders|front deltoids|triceps;core|landmine|strength|unilateral|false|standard
cable_lateral_raise|Cable Lateral Raise|cable side raise|Shoulders|side deltoids||cable|strength|unilateral|false|standard
machine_lateral_raise|Machine Lateral Raise|lateral raise machine|Shoulders|side deltoids||selectorized machine|strength|bilateral|false|standard
reverse_pec_deck|Reverse Pec Deck|machine rear delt fly|Shoulders|rear deltoids|upper back|selectorized machine|strength|bilateral|false|standard
band_pull_apart|Band Pull-Apart|band reverse fly|Shoulders|rear deltoids;upper back||resistance band|strength|bilateral|false|standard
upright_row|Cable Upright Row|upright row|Shoulders|side deltoids;traps|biceps|cable|strength|bilateral|false|standard
barbell_curl|Barbell Curl|straight bar curl|Arms|biceps|forearms|barbell|strength|bilateral|false|standard
ez_bar_curl|EZ-Bar Curl|ez curl|Arms|biceps|forearms|ez bar|strength|bilateral|false|standard
incline_db_curl|Incline Dumbbell Curl|incline curl|Arms|biceps||dumbbell;bench|strength|bilateral|false|standard
preacher_curl|Preacher Curl|preacher bench curl|Arms|biceps||ez bar;preacher bench|strength|bilateral|false|standard
cable_curl|Cable Curl|standing cable curl|Arms|biceps||cable|strength|bilateral|false|standard
concentration_curl|Concentration Curl|seated single arm curl|Arms|biceps||dumbbell|strength|unilateral|false|standard
reverse_curl|Reverse Curl|pronated curl|Arms|forearms;biceps||barbell|strength|bilateral|false|standard
overhead_triceps_extension|Overhead Triceps Extension|overhead extension|Arms|triceps||dumbbell|strength|bilateral|false|standard
cable_overhead_triceps_extension|Cable Overhead Triceps Extension|rope overhead extension|Arms|triceps||cable|strength|bilateral|false|standard
close_grip_bench_press|Close-Grip Bench Press|narrow grip bench|Arms|triceps|chest|barbell;bench|strength|bilateral|false|standard
machine_triceps_extension|Machine Triceps Extension|triceps machine|Arms|triceps||selectorized machine|strength|bilateral|false|standard
wrist_curl|Wrist Curl|forearm curl|Arms|forearms||dumbbell|strength|bilateral|false|standard
reverse_wrist_curl|Reverse Wrist Curl|wrist extension|Arms|forearms||dumbbell|strength|bilateral|false|standard
plate_pinch|Plate Pinch Hold|plate pinch|Arms|forearms;grip||weight plate|strength|bilateral|false|hold
dead_hang|Dead Hang|bar hang|Arms|grip;forearms|lats|pull-up bar|bodyweight|bilateral|true|hold_body
front_squat|Front Squat|barbell front squat|Legs|quadriceps;glutes|core|barbell;rack|strength|bilateral|false|standard
box_squat|Box Squat|barbell box squat|Legs|quadriceps;glutes|hamstrings|barbell;box|strength|bilateral|false|standard
smith_machine_squat|Smith Machine Squat|smith squat|Legs|quadriceps;glutes|hamstrings|smith machine|strength|bilateral|false|standard
hack_squat|Hack Squat|machine hack squat|Legs|quadriceps;glutes|hamstrings|plate-loaded machine|strength|bilateral|false|standard
belt_squat|Belt Squat|machine belt squat|Legs|quadriceps;glutes|hamstrings|plate-loaded machine|strength|bilateral|false|standard
split_squat|Split Squat|stationary lunge|Legs|quadriceps;glutes|hamstrings|dumbbell|strength|unilateral|true|standard_body
walking_lunge|Walking Lunge|dumbbell walking lunge|Legs|quadriceps;glutes|hamstrings|dumbbell|strength|unilateral|true|standard_body
reverse_lunge|Reverse Lunge|backward lunge|Legs|quadriceps;glutes|hamstrings|dumbbell|strength|unilateral|true|standard_body
step_up|Dumbbell Step Up|box step up|Legs|quadriceps;glutes|hamstrings|dumbbell;box|strength|unilateral|false|standard
leg_extension|Leg Extension|quad extension|Legs|quadriceps||selectorized machine|strength|bilateral|false|standard
lying_leg_curl|Lying Leg Curl|prone hamstring curl|Legs|hamstrings||selectorized machine|strength|bilateral|false|standard
standing_leg_curl|Standing Single-Leg Curl|unilateral hamstring curl|Legs|hamstrings||selectorized machine|strength|unilateral|false|standard
stiff_leg_deadlift|Stiff-Leg Deadlift|straight leg deadlift|Legs|hamstrings|glutes;lower back|barbell|strength|bilateral|false|standard
single_leg_rdl|Single-Leg Romanian Deadlift|single leg rdl|Legs|hamstrings;glutes|core|dumbbell|strength|unilateral|false|standard
trap_bar_deadlift|Trap Bar Deadlift|hex bar deadlift|Legs|quadriceps;glutes|hamstrings;back|trap bar|strength|bilateral|false|standard
sumo_deadlift|Sumo Deadlift|wide stance deadlift|Legs|glutes;quadriceps|hamstrings;back|barbell|strength|bilateral|false|standard
glute_bridge|Glute Bridge|floor hip bridge|Legs|glutes|hamstrings|bodyweight|bodyweight|bilateral|true|body
single_leg_glute_bridge|Single-Leg Glute Bridge|unilateral glute bridge|Legs|glutes|hamstrings|bodyweight|bodyweight|unilateral|true|body
cable_pull_through|Cable Pull-Through|rope pull through|Legs|glutes;hamstrings|lower back|cable|strength|bilateral|false|standard
cable_hip_abduction|Cable Hip Abduction|standing hip abduction|Legs|glutes||cable|strength|unilateral|false|standard
hip_abduction_machine|Hip Abduction Machine|seated hip abduction|Legs|glutes||selectorized machine|strength|bilateral|false|standard
hip_adduction_machine|Hip Adduction Machine|seated hip adduction|Legs|adductors||selectorized machine|strength|bilateral|false|standard
seated_calf_raise|Seated Calf Raise|bent knee calf raise|Legs|calves||plate-loaded machine|strength|bilateral|false|standard
leg_press_calf_raise|Leg Press Calf Raise|calf press|Legs|calves||plate-loaded machine|strength|bilateral|false|standard
single_leg_calf_raise|Single-Leg Calf Raise|unilateral calf raise|Legs|calves||bodyweight|bodyweight|unilateral|true|standard_body
ab_wheel_rollout|Ab Wheel Rollout|wheel rollout|Core|core|lats|ab wheel|bodyweight|bilateral|true|body
cable_crunch|Cable Crunch|kneeling cable crunch|Core|core||cable|strength|bilateral|false|standard
reverse_crunch|Reverse Crunch|lying reverse crunch|Core|core|hip flexors|bodyweight|bodyweight|bilateral|true|body
russian_twist|Russian Twist|seated twist|Core|obliques;core||medicine ball|strength|bilateral|false|standard
pallof_press|Pallof Press|anti rotation press|Core|core;obliques||cable|strength|bilateral|false|hold_load
side_plank|Side Plank|lateral plank|Core|obliques;core||bodyweight|bodyweight|unilateral|true|hold_body
bird_dog|Bird Dog|quadruped reach|Core|core|glutes|bodyweight|bodyweight|unilateral|true|body
dead_bug|Dead Bug|supine march|Core|core|hip flexors|bodyweight|bodyweight|unilateral|true|body
power_clean|Power Clean|hang power clean|Full Body|glutes;quadriceps;traps|hamstrings;shoulders|barbell|conditioning|bilateral|false|standard
hang_high_pull|Hang High Pull|barbell high pull|Full Body|traps;glutes|hamstrings;shoulders|barbell|conditioning|bilateral|false|standard
push_press|Push Press|barbell push press|Full Body|shoulders;quadriceps|triceps;glutes|barbell|conditioning|bilateral|false|standard
dumbbell_thruster|Dumbbell Thruster|squat to press|Full Body|quadriceps;shoulders|glutes;triceps|dumbbell|conditioning|bilateral|false|standard
turkish_get_up|Turkish Get-Up|tgu|Full Body|shoulders;core|glutes|kettlebell|strength|unilateral|false|standard
suitcase_carry|Suitcase Carry|single arm farmers walk|Full Body|core;forearms|traps;legs|dumbbell|conditioning|unilateral|false|carry
front_rack_carry|Front Rack Carry|kettlebell rack walk|Full Body|core;upper back|legs;forearms|kettlebell|conditioning|bilateral|false|carry
sled_push|Sled Push|prowler push|Full Body|quadriceps;glutes|calves;shoulders|sled|conditioning|bilateral|false|carry
rowing_machine|Rowing Machine|erg row|Cardio|legs;back|arms;core|rowing machine|cardio|bilateral|true|cardio
stationary_bike|Stationary Bike|exercise bike;spin bike|Cardio|legs|calves|cardio equipment|cardio|bilateral|true|cardio
stair_climber|Stair Climber|stepmill|Cardio|quadriceps;glutes|calves|cardio equipment|cardio|bilateral|true|cardio
battle_ropes|Battle Ropes|rope waves|Cardio|shoulders;arms|core;legs|battle ropes|conditioning|bilateral|false|conditioning
'@ -split "`n" | Where-Object { $_.Trim() }

$capabilities = @{
    standard=@('repetitions','load','rpe','tempo'); body=@('repetitions','bodyweight','weighted_bodyweight','rpe','tempo');
    body_assisted=@('repetitions','bodyweight','weighted_bodyweight','assisted_load','rpe'); assisted=@('repetitions','bodyweight','assisted_load','rpe','tempo');
    standard_body=@('repetitions','load','bodyweight','rpe','tempo'); hold=@('duration','load','rpe'); hold_body=@('duration','bodyweight','weighted_bodyweight','rpe');
    hold_load=@('duration','load','rpe'); carry=@('load','duration','distance','rpe'); cardio=@('duration','distance','rpe'); conditioning=@('repetitions','duration','rpe')
}
function Parts([string]$value) {
    if ([string]::IsNullOrWhiteSpace($value)) { return ,([string[]]@()) }
    return ,([string[]]($value -split ';'))
}
$s6Ids = @($specs | ForEach-Object { ($_ -split '\|', 2)[0] })
$document.exercises = @($document.exercises | Where-Object { $_.id -notin $s6Ids })
$existingIds = @{}; foreach ($exercise in $document.exercises) { $existingIds[$exercise.id] = $true }
foreach ($line in $specs) {
    $p = $line -split '\|', 11
    if ($existingIds.ContainsKey($p[0])) { throw "Duplicate governed ID: $($p[0])" }
    $document.exercises += [pscustomobject][ordered]@{
        id=$p[0]; name=$p[1]; aliases=Parts $p[2]; category=$p[3]; primaryMuscles=Parts $p[4]; secondaryMuscles=Parts $p[5]
        equipment=Parts $p[6]; type=$p[7]; capabilities=$capabilities[$p[10]]; laterality=$p[8]; bodyweight=[bool]::Parse($p[9]); active=$true
    }
    $existingIds[$p[0]] = $true
}
$document.catalogueVersion = '2026.08.2'
$document.releasedAt = '2026-08-09T00:00:00Z'
$document.exerciseCount = $document.exercises.Count
$document.payloadChecksum = ''
[IO.File]::WriteAllText((Resolve-Path $Path), ($document | ConvertTo-Json -Depth 20 -Compress), [Text.UTF8Encoding]::new($false))
& "$PSScriptRoot/update-strength-catalogue.ps1" -Path $Path
