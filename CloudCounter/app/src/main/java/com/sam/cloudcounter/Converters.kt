package com.sam.cloudcounter

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken
import java.util.Date

class Converters {
    private val gson = Gson()
    private val stringListType = object : TypeToken<List<String>>() {}.type
    private val intListType    = object : TypeToken<List<Int>>()    {}.type

    @TypeConverter
    fun fromStringList(value: String?): List<String> {
        if (value.isNullOrBlank()) return emptyList()
        return try {
            gson.fromJson(value, stringListType)
        } catch (e: JsonSyntaxException) {
            // fallback: treat the entire column as one element
            listOf(value)
        }
    }

    @TypeConverter
    fun fromActivityType(type: ActivityType?): String? = type?.name

    @TypeConverter
    fun toActivityType(type: String?): ActivityType? =
        type?.let { ActivityType.valueOf(it) }

    @TypeConverter
    fun listToString(list: List<String>?): String {
        if (list == null) return "[]"
        return gson.toJson(list)
    }

    @TypeConverter
    fun fromIntList(value: String?): List<Int> {
        if (value.isNullOrBlank()) return emptyList()
        return try {
            gson.fromJson(value, intListType)
        } catch (e: JsonSyntaxException) {
            // fallback: parse single integer if possible, else empty
            value.toIntOrNull()?.let { listOf(it) } ?: emptyList()
        }
    }

    @TypeConverter
    fun intListToString(list: List<Int>?): String {
        if (list == null) return "[]"
        return gson.toJson(list)
    }

    // Date converters for Stash entities
    @TypeConverter
    fun fromTimestamp(value: Long?): Date? {
        return value?.let { Date(it) }
    }

    @TypeConverter
    fun dateToTimestamp(date: Date?): Long? {
        return date?.time
    }

    // StashEntryType converters
    @TypeConverter
    fun fromStashEntryType(type: StashEntryType?): String? = type?.name

    @TypeConverter
    fun toStashEntryType(value: String?): StashEntryType? =
        value?.let {
            try {
                StashEntryType.valueOf(it)
            } catch (e: IllegalArgumentException) {
                null
            }
        }
}