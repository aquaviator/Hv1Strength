import re

with open("app/src/main/java/com/example/core/sync/SyncEngineImpl.kt", "r") as f:
    content = f.read()

# To docData
docData_replacement = """                    docData["dateOfBirth"] = profile.dateOfBirth
                    docData["sex"] = profile.sex
                    docData["trainingExperience"] = profile.trainingExperience
                    docData["trialStartedAt"] = profile.trialStartedAt
                    docData["trialEndsAt"] = profile.trialEndsAt
                    docData["updatedAt"] = profile.updatedAt"""

content = re.sub(r'                    docData\["dateOfBirth"\] = profile.dateOfBirth\n                    docData\["sex"\] = profile.sex\n                    docData\["trainingExperience"\] = profile.trainingExperience\n                    docData\["updatedAt"\] = profile.updatedAt', docData_replacement, content)

# From doc (insert)
insert_replacement = """                    dateOfBirth = doc.getString("dateOfBirth"),
                    sex = doc.getString("sex"),
                    trainingExperience = doc.getString("trainingExperience"),
                    trialStartedAt = doc.getLong("trialStartedAt"),
                    trialEndsAt = doc.getLong("trialEndsAt"),
                    globalId = doc.getString("globalId") ?: "","""

content = re.sub(r'                    dateOfBirth = doc.getString\("dateOfBirth"\),\n                    sex = doc.getString\("sex"\),\n                    trainingExperience = doc.getString\("trainingExperience"\),\n                    globalId = doc.getString\("globalId"\) \?: "",', insert_replacement, content)

# From doc (update)
update_replacement = """                    dateOfBirth = doc.getString("dateOfBirth") ?: local.dateOfBirth,
                    sex = doc.getString("sex") ?: local.sex,
                    trainingExperience = doc.getString("trainingExperience") ?: local.trainingExperience,
                    trialStartedAt = doc.getLong("trialStartedAt") ?: local.trialStartedAt,
                    trialEndsAt = doc.getLong("trialEndsAt") ?: local.trialEndsAt,
                    updatedAt = doc.getLong("updatedAt") ?: now,"""

content = re.sub(r'                    dateOfBirth = doc.getString\("dateOfBirth"\) \?: local.dateOfBirth,\n                    sex = doc.getString\("sex"\) \?: local.sex,\n                    trainingExperience = doc.getString\("trainingExperience"\) \?: local.trainingExperience,\n                    updatedAt = doc.getLong\("updatedAt"\) \?: now,', update_replacement, content)


with open("app/src/main/java/com/example/core/sync/SyncEngineImpl.kt", "w") as f:
    f.write(content)
