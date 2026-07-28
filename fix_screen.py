import re

with open("app/src/main/java/com/example/ui/screens/SubscriptionAccessScreen.kt", "r") as f:
    content = f.read()

old_code = """                            val priceCopy = when {
                                productInfo != null -> {
                                    if (productInfo?.hasFreeTrial == true) {
                                        "1 month free, then ${productInfo?.formattedPrice}/year"
                                    } else {
                                        "${productInfo?.formattedPrice}/year"
                                    }
                                }
                                else -> CommercialConfig.PLANNED_UK_PRICE + "/year (1 month free trial available via Play)"
                            }"""

new_code = """                            val priceCopy = when {
                                productInfo != null -> {
                                    if (productInfo?.hasFreeTrial == true) {
                                        "1 month free, then ${productInfo?.formattedPrice}/year"
                                    } else {
                                        "${productInfo?.formattedPrice}/year"
                                    }
                                }
                                else -> CommercialConfig.PLANNED_UK_PRICE + "/year"
                            }"""

content = content.replace(old_code, new_code)

with open("app/src/main/java/com/example/ui/screens/SubscriptionAccessScreen.kt", "w") as f:
    f.write(content)
