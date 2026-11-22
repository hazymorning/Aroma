# Retrofit reads the generic return types of MealieApi (for example PageDto<RecipeSummaryDto>) at runtime.
-keepattributes Signature, InnerClasses, EnclosingMethod, RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keep,allowobfuscation,allowshrinking interface app.kochi.data.remote.MealieApi
