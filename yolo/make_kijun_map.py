"""YOLO 클래스 → 검사기준 통합검색 매핑표 만들기.

입력: yolo/data.yaml, yolo/엘리베이터_객체인식_클래스_1차.xlsx ('클래스' 시트)  ← 원본은 qwertyyo/kijun 의 yolo/
출력: app/src/main/assets/kijun_class_map.json  (앱이 읽음: KijunClassMap.kt)

- 검색어(query) = 엑셀 '별칭·현장용어' 의 첫째 값(kijun 이 대표 검색어로 정한 것).
  바꾸고 싶으면 아래 QUERY_OVERRIDE 에 적는다.
- 근거 조항(clauses) = '근거 조항(별표22, 연혁집 현행)' 칸의 조항 번호와 제목.
- legacy = 예전 모델(5개 클래스)·저장된 검사 기록의 클래스 이름 → 새 클래스 키.

실행: D:\\elevator_dataset\\.venv\\Scripts\\python.exe yolo\\make_kijun_map.py   (openpyxl 필요)
"""
import json
import re
import sys
from pathlib import Path

import openpyxl

HERE = Path(__file__).resolve().parent
XLSX = HERE / "엘리베이터_객체인식_클래스_1차.xlsx"
DATA_YAML = HERE / "data.yaml"
OUT = HERE.parent / "app" / "src" / "main" / "assets" / "kijun_class_map.json"

# 대표 검색어를 엑셀과 다르게 쓰고 싶을 때만 (클래스 키 → 검색어).
# 2026-10-20 실제 사이트에서 75개 검색어를 돌려 보고, 엑셀 첫 별칭으로는 근거 조항이 상위에
# 안 나오는데 다른 별칭으로는 훨씬 위에 나오는 것만 바꿨다(엘리베이터 탭 기준 순위).
QUERY_OVERRIDE: dict[str, str] = {
    "pit_ladder": "피트 사다리",     # 사다리 → Ⅶ 8위, 피트 사다리 → 2위
    "leveling_device": "착상",       # 차폐판 → 10위 밖, 착상 → 16.1.1 2위
    "warning_sign": "경고",          # 표지 → 7위, 경고 → 6.4 1위
    "door_link_rope": "연동",        # 연동로프 → 결과 없음, 연동 → 7.11 8위
    # 2026-11-01 사이트 검색 순위 수정 뒤 다시 돌려 본 결과 (전체 탭 기준)
    "door_open_button": "문열림버튼",  # 열림버튼 → 불합격 7.6.3 1위·기준 2위, 문열림버튼 → 기준 7.6.3 1위
    "hall_button": "호출버튼",         # 호출 → 피난호출 등 15위, 호출버튼 → 17.1.4.1 1위
    "indicator": "위치표시기",         # 표시기 → 16.4.3 3위, 위치표시기 → 1위
    "car_handrail": "카 손잡이",       # 손잡이 → 에스컬레이터 핸드레일 섞임, 카 손잡이 → 8.3.3 1위
    "manual_handwheel": "비상운전 핸들",  # 핸들 → 에스컬레이터 수동핸들 섞임
    "cop": "카 내 조작반",             # 조작반 → 점검운전 조작반 1위
    "fire_switch": "소방구조용 엘리베이터 스위치",  # 소방운전 스위치 → 사전이 소방구조용 전체로 넓혀 17.2.3.1 등, 이것 → 17.2.8.1~3
}

# 예전 클래스 이름 → 새 클래스 키
LEGACY = {
    "block": "tactile_block",
    "car_button_emerg": "emergency_call",
    "car_button_open": "door_open_button",
    "indicator": "indicator",
    "toe_guard": "toe_guard",
    "car_button_panel": "cop",
    "counterweight_screen": "counterweight_screen",
    "suspension_belt": "suspension_rope",   # 45 현수 벨트 → 3 주로프로 통합 (2026-10-31)
}


def read_yaml_names(path: Path) -> list[str]:
    names = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        m = re.match(r"\s+(\d+):\s*([A-Za-z0-9_]+)", line)
        if m:
            names[int(m.group(1))] = m.group(2)
    if sorted(names) != list(range(len(names))):
        sys.exit(f"data.yaml 번호가 0부터 이어지지 않음: {sorted(names)}")
    return [names[i] for i in range(len(names))]


def parse_clauses(cell: str) -> list[dict]:
    out = []
    for part in (cell or "").split(" / "):
        part = part.strip()
        if not part:
            continue
        num, _, title = part.partition(" ")
        out.append({"num": num, "title": title.strip()})
    return out


def main() -> None:
    names = read_yaml_names(DATA_YAML)

    ws = openpyxl.load_workbook(XLSX, read_only=True)["클래스"]
    rows = list(ws.iter_rows(values_only=True))
    header = [str(h) if h is not None else "" for h in rows[0]]

    def col(prefix: str) -> int:
        for i, h in enumerate(header):
            if h.startswith(prefix):
                return i
        sys.exit(f"엑셀 '클래스' 시트에 '{prefix}' 칸이 없음: {header}")

    c_id, c_key, c_ko, c_loc = col("id"), col("클래스 키"), col("한글 이름"), col("위치")
    c_alias, c_clause = col("별칭"), col("근거 조항")

    classes = []
    for r in rows[1:]:
        if r[c_key] is None:
            continue
        cid, key = int(r[c_id]), str(r[c_key]).strip()
        if cid >= len(names) or names[cid] != key:
            sys.exit(f"엑셀 {cid} {key} 가 data.yaml 과 다름 (data.yaml: {names[cid] if cid < len(names) else '없음'})")
        aliases = [a.strip() for a in str(r[c_alias] or "").split(",") if a.strip()]
        query = QUERY_OVERRIDE.get(key) or (aliases[0] if aliases else str(r[c_ko]))
        name_ko = str(r[c_ko]).strip()
        classes.append({
            "id": cid,
            "key": key,
            "name_ko": name_ko,
            "location": str(r[c_loc] or "").strip(),
            "query": query,
            "aliases": aliases,
            "clauses": parse_clauses(str(r[c_clause] or "")),
            # 엑셀 한글 이름이 "(삭제 …" / "(통합 …" 이면 번호만 남긴 클래스: 앱이 인식·검색 목록에서 뺀다
            "deleted": name_ko.startswith(("(삭제", "(통합")),
        })

    if [c["key"] for c in classes] != names:
        missing = sorted(set(names) - {c["key"] for c in classes})
        sys.exit(f"엑셀과 data.yaml 의 클래스가 다름. 엑셀에 없는 것: {missing}")

    keys = set(names)
    bad = {k: v for k, v in LEGACY.items() if v not in keys}
    if bad:
        sys.exit(f"legacy 대상이 data.yaml 에 없음: {bad}")

    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(
        json.dumps({
            "source": "qwertyyo/kijun yolo/ (data.yaml + 엘리베이터_객체인식_클래스_1차.xlsx)",
            "classes": classes,
            "legacy": LEGACY,
        }, ensure_ascii=False, indent=1) + "\n",
        encoding="utf-8",
        newline="\n",
    )
    print(f"{OUT}  클래스 {len(classes)}개, legacy {len(LEGACY)}개")


if __name__ == "__main__":
    main()
