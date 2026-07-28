import re

with open("app/src/main/java/com/example/core/sync/SyncEngineImpl.kt", "r") as f:
    content = f.read()

docData_replacement = """                    docData["dateOfBirth"] = profile.dateOfBirth
                    docData["sex"] = profile.sex
                    docData["trainingExperience"] = profile.trainingExperience
                    docData["trialStartedAt"] = profile.trialStartedAt
                    docData["trialEndsAt"] = profile.trialEndsAt
                    docData["createdAt"] = profile.createdAt"""

content = re.sub(r'                    docData\["dateOfBirth"\] = profile.dateOfBirth\n                    docData\["sex"\] = profile.sex\n                    docData\["trainingExperience"\] = profile.trainingExperience\n                    docData\["createdAt"\] = profile.createdAt', docData_replacement, content)

with open("app/src/main/java/com/example/core/sync/SyncEngineImpl.kt", "w") as f:
    f.write(content)
