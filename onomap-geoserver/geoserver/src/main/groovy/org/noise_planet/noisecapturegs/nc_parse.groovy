/*
 * This file is part of the NoiseCapture application and OnoMap system.
 *
 * The 'OnoMaP' system is led by Lab-STICC and Ifsttar and generates noise maps via
 * citizen-contributed noise data.
 *
 * This application is co-funded by the ENERGIC-OD Project (European Network for
 * Redistributing Geospatial Information to user Communities - Open Data). ENERGIC-OD
 * (http://www.energic-od.eu/) is partially funded under the ICT Policy Support Programme (ICT
 * PSP) as part of the Competitiveness and Innovation Framework Programme by the European
 * Community. The application work is also supported by the French geographic portal GEOPAL of the
 * Pays de la Loire region (http://www.geopal.org).
 *
 * Copyright (C) 2007-2016 - IFSTTAR - LAE
 * Lab-STICC – CNRS UMR 6285 Equipe DECIDE Vannes
 *
 * NoiseCapture is a free software; you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation; either version 3 of
 * the License, or(at your option) any later version. NoiseCapture is distributed in the hope that
 * it will be useful,but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for
 * more details.You should have received a copy of the GNU General Public License along with this
 * program; if not, write to the Free Software Foundation,Inc., 51 Franklin Street, Fifth Floor,
 * Boston, MA 02110-1301  USA or see For more information,  write to Ifsttar,
 * 14-20 Boulevard Newton Cite Descartes, Champs sur Marne F-77447 Marne la Vallee Cedex 2 FRANCE
 *  or write to scientific.computing@ifsttar.fr
 */

package org.noise_planet.noisecapturegs

import groovy.sql.GroovyRowResult
import groovy.sql.Sql
import geoserver.GeoServer

import java.security.InvalidParameterException
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException
import java.sql.Timestamp
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

title = 'nc_parse'
description = 'Parse uploaded zip files'

inputs = [
]

outputs = [
        result: [name: 'result', title: 'Processed files', type: Integer.class]
]

class ZipFileFilter implements FilenameFilter {
    @Override
    boolean accept(File dir, String name) {
        return name.toLowerCase().endsWith(".zip")
    }
}

def processFile(Connection connection, File zipFile) {
    try {
        connection.setAutoCommit(false)
        def sql = new Sql(connection)
        // Fetch metadata
        Properties meta = null;
        zipFile.withInputStream { is ->
            ZipInputStream zipInputStream = new ZipInputStream(is)
            ZipEntry zipEntry
            while ((zipEntry = zipInputStream.getNextEntry()) != null) {
                if ("meta.properties".equals(zipEntry.getName())) {
                    meta = new Properties();
                    meta.load(zipInputStream)
                }
            }
        }
        if (meta == null) {
            throw new InvalidParameterException("No meta file")
        }
        // Check content
        // Check uuid
        if (!(meta.getProperty("uuid") ==~ '[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}')) {
            // Wrong UUID
            throw new InvalidParameterException("Wrong UUID \"" + meta.getProperty("uuid") + "\"")
        }
        if (!(Integer.valueOf(meta.getProperty("version_number")) > 0)) {
            throw new InvalidParameterException("Wrong version \"" + meta.getProperty("version_number") + "\"")
        }
        if (!(Double.valueOf(meta.getProperty("leq_mean").replace(",", ".")) > 0)) {
            throw new InvalidParameterException("Wrong leq_mean \"" + meta.getProperty("leq_mean") + "\"")
        }
        if (!(Integer.valueOf(meta.getProperty("pleasantness")) >= 0 &&
                Integer.valueOf(meta.getProperty("pleasantness")) <= 100)) {
            throw new InvalidParameterException("Wrong pleasantness \"" + meta.getProperty("pleasantness") + "\"")
        }
        // Fetch or insert user
        GroovyRowResult res = sql.firstRow("SELECT * FROM noisecapture_user WHERE user_uuid=:uuid",
                [uuid: meta.getProperty("uuid")])
        def idUser
        if (res == null) {
            // Create user
            idUser = sql.executeInsert("INSERT INTO noisecapture_user(user_uuid, date_creation) VALUES (:uuid, current_date)",
                    [uuid: meta.getProperty("uuid")])[0][0]
        } else {
            idUser = res.get("pk_user")
        }
        // insert record
        Map record = [pk_user            : idUser,
                      version_number     : meta.getProperty("version_number"),
                      record_utc         : new Timestamp(Long.valueOf(meta.getProperty("record_utc"))),
                      pleasantness       : meta.getOrDefault("pleasantness", null),
                      device_product     : meta.get("device_product"),
                      device_model       : meta.get("device_model"),
                      device_manufacturer: meta.get("device_manufacturer"),
                      noise_level        : Double.valueOf(meta.getProperty("leq_mean").replace(",", ".")),
                      time_length        : meta.get("time_length")]
        int recordId = sql.executeInsert("INSERT INTO noisecapture_track(pk_user, version_number, record_utc," +
                " pleasantness, device_product, device_model, device_manufacturer, noise_level, time_length) VALUES (" +
                " :pk_user, :version_number, :record_utc, :pleasantness, :device_product, :device_model," +
                " :device_manufacturer, :noise_level, :time_length)", record)[0][0]
        // insert tags
        if (meta.hasProperty("tags")) {
            // Cache tags
            Map<String, Integer> tagToIdTag = new HashMap<>()
            sql.eachRow("SELECT pk_tag, tag_name FROM noisecapture_tag") { GroovyRowResult row ->
                tagToIdTag.put(row.tag_name, row.pk_tag)
            }
            // Insert tags
            meta.getProperty("tags").tokenize(",").each () { String tag ->
                def tagId = tagToIdTag.get(tag.toLowerCase())
                if(tagId == null) {
                    // Insert new tag
                    tagId = sql.executeInsert("INSERT INTO noisecapture_tag (tag_name) VALUES (:tag_name)",
                            [tag_name:tag.toLowerCase()])[0][0]
                    tagToIdTag.put(tag.toLowerCase(), (int)tagId)
                }
                sql.executeInsert("INSERT INTO noisecapture_track_tag VALUES (:pktrack, :pktag)",
                        [pktrack:recordId, pktag:tagId])
            }
        }
        // Fetch GeoJSON

        // Accept changes
        connection.commit();
    } catch (SQLException ex) {
        connection.rollback();
        throw ex
    } finally {
        // Move file to processed folder
        File processedDir = new File("data_dir/onomap_archive");
        if (!processedDir.exists()) {
            processedDir.mkdirs()
        }
        zipFile.renameTo(new File(processedDir, zipFile.getName()))
    }
}

def Connection openPostgreSQLConnection() {
    // Init driver
    Class.forName("org.postgresql.Driver");
    // Read login/pass/dbname
    Properties properties = new Properties();
    File configFile = new File("onomapdb.properties")
    if (configFile.exists()) {
        properties.load(new FileReader(configFile))
        Connection connection = null;
        return DriverManager.getConnection(
                "jdbc:postgresql://localhost:" + properties.getProperty("port") + "/" + properties.getProperty("dbname"),
                properties.getProperty("username"), properties.getProperty("password"));
    } else {
        return null
    }
}


def Connection openPostgreSQLDataStoreConnection() {
    new GeoServer().catalog.getStore("postgis").
}
def run(input) {
    File dataDir = new File("data_dir/onomap_uploading");
    if (dataDir.exists()) {
        File[] files = dataDir.listFiles(new ZipFileFilter())
        if (files.length != 0) {
            // Open PostgreSQL connection
            Connection connection = openPostgreSQLConnection()
            try {
                for (File zipFile : files) {
                    processFile(connection, zipFile)
                }
            } finally {
                connection.close()
            }
        }
    }
    return 0;
}