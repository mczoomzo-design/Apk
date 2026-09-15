/**
 * PhotoSearch — Apps Script สำหรับงานประสานงาน/จัดการ (ไม่ใช่เส้นทางค้นหา)
 *
 * บทบาท (ตามสถาปัตยกรรม):
 *   - จุดเผยแพร่ current.json "เพียงจุดเดียว" ด้วย ScriptLock + expectedRevision
 *   - จัดการกิจกรรม: สร้าง/แก้ event.json, สร้างลิงก์+QR
 *   ไม่ใช้เป็นที่พักดัชนีหลัก และไม่รับ traffic ค้นหา (Apps Script มีเวลารัน/แคชจำกัด)
 *
 * ข้อจำกัดที่อ้างอิง:
 *   - เวลารันต่อ execution จำกัด, CacheService จำกัด 100KB/key และถูกนำออกก่อนหมดอายุได้
 *   - LockService ใช้กันการเผยแพร่ชนกัน (ถือ lock สั้น ๆ เท่านั้น ห้ามถือระหว่าง AI/ดาวน์โหลด)
 *
 * ตั้งค่า Script Property: ROOT_FOLDER_ID = fileId ของโฟลเดอร์ราก PhotoSearch/ (Shared Drive)
 */

function _rootId() {
  var id = PropertiesService.getScriptProperties().getProperty('ROOT_FOLDER_ID');
  if (!id) throw new Error('ยังไม่ตั้ง Script Property ROOT_FOLDER_ID');
  return id;
}

/** หา/สร้างโฟลเดอร์ย่อยตาม path เชิงตรรกะภายในราก (คืน Folder) */
function _resolveFolder(parts, create) {
  var folder = DriveApp.getFolderById(_rootId());
  for (var i = 0; i < parts.length; i++) {
    var it = folder.getFoldersByName(parts[i]);
    if (it.hasNext()) {
      folder = it.next();
    } else if (create) {
      folder = folder.createFolder(parts[i]);
    } else {
      return null;
    }
  }
  return folder;
}

function _readJson(pathParts) {
  var name = pathParts.pop();
  var folder = _resolveFolder(pathParts, false);
  if (!folder) return null;
  var files = folder.getFilesByName(name);
  if (!files.hasNext()) return null;
  return JSON.parse(files.next().getBlob().getDataAsString());
}

function _writeJson(pathParts, obj) {
  var name = pathParts.pop();
  var folder = _resolveFolder(pathParts, true);
  var files = folder.getFilesByName(name);
  var content = JSON.stringify(obj, null, 2);
  if (files.hasNext()) {
    files.next().setContent(content);
  } else {
    folder.createFile(name, content, 'application/json');
  }
}

/**
 * เผยแพร่ generation → current.json (จุด commit เดียว)
 * ตรวจ expectedRevision กันการเขียนทับ และ job/generation ต้องมาจากรุ่นล่าสุด
 * งานเก่าที่หมดสิทธิ์/สร้างจาก revision เก่าจะถูกปฏิเสธ (ให้สร้างใหม่จากข้อมูลล่าสุด)
 */
function publishCurrent(eventId, generationId, expectedRevision) {
  var lock = LockService.getScriptLock();
  if (!lock.tryLock(15000)) {
    return { ok: false, error: 'busy: ไม่ได้ lock' };
  }
  try {
    var cur = _readJson(['events', eventId, 'current.json']);
    var curRev = cur ? (cur.revision || 0) : 0;
    if (curRev !== expectedRevision) {
      return { ok: false, error: 'conflict', currentRevision: curRev };
    }
    // ตรวจว่า generation มีอยู่จริงและ manifest ครบ (ตรวจ checksum ทำฝั่ง worker/API แล้ว)
    var manifest = _readJson(['events', eventId, 'generations', generationId, 'manifest.json']);
    if (!manifest) {
      return { ok: false, error: 'generation ไม่พบ manifest' };
    }
    var newRev = curRev + 1;
    _writeJson(['events', eventId, 'current.json'], {
      eventId: eventId,
      generationId: generationId,
      revision: newRev,
      publishedAt: new Date().toISOString()
    });
    _appendAudit(eventId, { action: 'publish', generationId: generationId, revision: newRev });
    return { ok: true, revision: newRev };
  } finally {
    lock.releaseLock();
  }
}

function _appendAudit(eventId, entry) {
  // เก็บ audit เป็นไฟล์รายวัน (ไม่เขียนไฟล์ใหม่ต่อทุกคำขอ)
  var day = Utilities.formatDate(new Date(), 'UTC', 'yyyy-MM-dd');
  var parts = ['events', eventId, 'audit', day + '.jsonl'];
  var name = parts.pop();
  var folder = _resolveFolder(parts, true);
  entry.at = new Date().toISOString();
  var line = JSON.stringify(entry) + '\n';
  var files = folder.getFilesByName(name);
  if (files.hasNext()) {
    var f = files.next();
    f.setContent(f.getBlob().getDataAsString() + line);
  } else {
    folder.createFile(name, line, 'text/plain');
  }
}

/** สร้าง/แก้ไขกิจกรรม (เขียน event.json) */
function upsertEvent(doc) {
  if (!doc.eventId) throw new Error('ต้องมี eventId');
  var existing = _readJson(['events', doc.eventId, 'event.json']);
  doc.schemaVersion = 1;
  doc.createdAt = existing ? existing.createdAt : new Date().toISOString();
  doc.updatedAt = new Date().toISOString();
  _writeJson(['events', doc.eventId, 'event.json'], doc);
  _appendAudit(doc.eventId, { action: 'upsertEvent' });
  return doc;
}

/** URL ค้นหาสำหรับผู้ร่วมกิจกรรม (ตั้ง WEB_BASE_URL ใน Script Properties) */
function eventUrl(eventId) {
  var base = PropertiesService.getScriptProperties().getProperty('WEB_BASE_URL') || 'https://example.com';
  return base.replace(/\/$/, '') + '/e/' + encodeURIComponent(eventId);
}

/** สร้าง QR (ใช้ Google Chart API หรือบริการ QR ที่ได้รับอนุญาต) */
function qrImageUrl(eventId) {
  var url = eventUrl(eventId);
  return 'https://quickchart.io/qr?size=400&text=' + encodeURIComponent(url);
}

/** endpoint สำหรับ worker/API เรียกเผยแพร่ (deploy เป็น Web App, จำกัดสิทธิ์เข้าถึง) */
function doPost(e) {
  var body = JSON.parse(e.postData.contents);
  var token = PropertiesService.getScriptProperties().getProperty('PUBLISH_TOKEN');
  if (!token || body.token !== token) {
    return ContentService.createTextOutput(JSON.stringify({ ok: false, error: 'unauthorized' }))
      .setMimeType(ContentService.MimeType.JSON);
  }
  var res;
  if (body.action === 'publish') {
    res = publishCurrent(body.eventId, body.generationId, body.expectedRevision);
  } else if (body.action === 'upsertEvent') {
    res = upsertEvent(body.event);
  } else {
    res = { ok: false, error: 'unknown action' };
  }
  return ContentService.createTextOutput(JSON.stringify(res)).setMimeType(ContentService.MimeType.JSON);
}
