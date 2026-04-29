package com.rel.languager

import android.content.res.Configuration
import android.content.res.Resources
import android.util.Log
import com.rel.languager.Constants.SHARED_PREF_FILE_NAME
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface
import java.lang.reflect.Constructor
import java.lang.reflect.Executable
import java.lang.reflect.Field
import java.util.*

class FeatureSpoofer : XposedModule() {
    private fun log(message: String, tr: Throwable? = null) {
        log(Log.INFO, null, "[Languager] $message", tr)
    }

    private val pref by lazy {
        getRemotePreferences(SHARED_PREF_FILE_NAME)
    }

    override fun onPackageLoaded(lpparam: XposedModuleInterface.PackageLoadedParam) {
        lpparam.packageName.let { packageName ->
            if (packageName == BuildConfig.APPLICATION_ID) {
                return
            }

            val languageCode = LanguageUtils.getLanguageForPackage(packageName, pref)

            if (languageCode == Constants.DEFAULT_LANGUAGE) {
                return
            }

            // Use Locale.forLanguageTag for long language codes
            hookLocaleAPIs(lpparam, Locale.forLanguageTag(languageCode))
        }
    }

    private fun hookLocaleAPIs(lpparam: XposedModuleInterface.PackageLoadedParam, locale: Locale) {
        try {
            hookCommonLocaleAPIs(lpparam, locale)
            hookApi24PlusLocaleAPIs(lpparam, locale)
        } catch (e: Exception) {
            log("Error during hooking process: ${e.message}")
            e.printStackTrace()
        }
    }

    fun findMethod(
        clazz: Class<*>,
        methodName: String,
        vararg parameterTypes: Class<*>?
    ): Executable {
        val method = if (methodName in listOf("<init>", "<clinit>"))
            clazz.getConstructor(*parameterTypes)
        else
            clazz.getDeclaredMethod(methodName, *parameterTypes)
        method.isAccessible = true
        return method
    }

    @Throws(NoSuchFieldException::class)
    inline fun <reified T> findField(fieldName: String): Field {
        val clazz: Class<*> = T::class.java
        val field = generateSequence(clazz) { it.superclass }
            .takeWhile { it != Any::class.java }
            .firstNotNullOfOrNull { currentClass ->
                try {
                    currentClass.getDeclaredField(fieldName)
                } catch (ignored: NoSuchFieldException) {
                    null // 没找到则返回 null，继续向父类寻找
                }
            } ?: throw NoSuchFieldException("Field '$fieldName' not found in class ${clazz.name} or its superclasses.")

        field.isAccessible = true
        return field
    }

    @Throws(NoSuchFieldException::class)
    inline fun <reified T> setField(obj: T, fieldName: String, newVal: Any?) {
        findField<T>(fieldName).set(obj, newVal)
    }

    fun Any.setObjField(fieldName: String, newVal: Any?) {
        val field = generateSequence(this::class.java) { it.superclass }
            .takeWhile { it != Any::class.java }
            .firstNotNullOfOrNull { currentClass ->
                try {
                    currentClass.getDeclaredField(fieldName)
                } catch (_: NoSuchFieldException) {
                    null // 没找到则返回 null，继续向父类寻找
                }
            }
            ?: throw NoSuchFieldException("Field '$fieldName' not found in class ${this::class.java.name} or its superclasses.")

        field.isAccessible = true
        field.set(this, newVal)
    }

    private fun hookCommonLocaleAPIs(
        lpparam: XposedModuleInterface.PackageLoadedParam,
        locale: Locale
    ) {
        try {
            hook(
                findMethod(
                    Locale::class.java,
                    "getDefault",
                )
            ).intercept {
                return@intercept locale
            }
        } catch (e: Throwable) {
            log("Error hooking Locale.getDefault(): ${e.message}")
        }

        try {
            hook(findMethod(Resources::class.java, "getConfiguration")).intercept {
                val conf = it.proceed() as Configuration
                findField<Configuration>("locale").set(conf, locale)
                return@intercept conf
            }
        } catch (e: Throwable) {
            log("Error hooking Resources.getConfiguration(): ${e.message}")
        }

        try {
            hook(
                findMethod(
                    Resources::class.java, "updateConfiguration",
                    Configuration::class.java, android.util.DisplayMetrics::class.java
                )
            ).intercept {
                val conf = it.args[0] as Configuration
                findField<Configuration>("locale").set(conf, locale)
                return@intercept it.proceed()
            }
        } catch (e: Throwable) {
            log("Error hooking Resources.updateConfiguration: ${e.message}")
        }

        try {
            hook(findMethod(Configuration::class.java, "setLocale", Locale::class.java)).intercept {
                findField<Configuration>("locale").set(it.thisObject, locale)
                it.proceed()
            }

        } catch (e: Throwable) {
            log("Error hooking Configuration.setLocale: ${e.message}")
        }


        try {
            hook(findMethod(Resources::class.java, "getSystem")).intercept {
                val resources = it.proceed() as Resources
                setField(resources.configuration, "locale", locale)
                resources
            }
        } catch (e: Throwable) {
            log("Error hooking Resources.getSystem(): ${e.message}")
        }

        try {
            hook(findMethod(Configuration::class.java, "<init>")).intercept {
                it.thisObject.setObjField("locale", locale)
                it.proceed()
            }
        } catch (e: Throwable) {
            log("Error hooking Configuration constructor: ${e.message}")
        }
    }

    private fun hookApi24PlusLocaleAPIs(
        lpparam: XposedModuleInterface.PackageLoadedParam,
        locale: Locale
    ) {
        try {

            hook(findMethod(Locale::class.java, "getDefault", Locale.Category::class.java)).intercept {
                return@intercept locale
            }
        } catch (e: Throwable) {
            log("Error hooking Locale.getDefault(Category): ${e.message}")
        }

        try {

            hook(findMethod(Configuration::class.java, "getLocales")).intercept {
                val constructor =
                    findMethod(android.os.LocaleList::class.java, "<init>", Array<Locale>::class.java) as Constructor<*>
                constructor.newInstance(arrayOf(locale))
            }
        } catch (e: Throwable) {
            log("Error hooking Configuration.getLocales(): ${e.message}")
        }

        try {
            hook(findMethod(Configuration::class.java, "setLocales", android.os.LocaleList::class.java)).intercept {
                it.proceed(arrayOf(android.os.LocaleList(locale)))
            }
        } catch (e: Throwable) {
            log("Error hooking Configuration.setLocales: ${e.message}")
        }

        try {
            hook(findMethod(android.os.LocaleList::class.java, "getDefault")).intercept {
                return@intercept android.os.LocaleList(locale)
            }
        } catch (e: Throwable) {
            log("Error hooking LocaleList.getDefault(): ${e.message}")
        }

        try {

            hook(findMethod(android.os.LocaleList::class.java, "getAdjustedDefault")).intercept {
                return@intercept android.os.LocaleList(locale)
            }
        } catch (e: Throwable) {
            log("Error hooking LocaleList.getAdjustedDefault(): ${e.message}")
        }
    }

}
