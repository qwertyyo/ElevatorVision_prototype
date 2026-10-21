# YOLO 클래스 확정본 (75개, 0~74)

**원본은 [qwertyyo/kijun](https://github.com/qwertyyo/kijun) 저장소의 `yolo/`** 입니다. 이 폴더의 `data.yaml`·엑셀은 그 복사본이라 여기서 고치지 않습니다.
고칠 일이 있으면 kijun 의 `yolo/classes.py` → `build_xlsx.py` 로 다시 만든 뒤 두 파일을 이쪽으로 다시 복사합니다.

- 복사한 원본: kijun 커밋 `97cf2e2` (2026-10-20, 74 `tactile_block` 추가 + 번호 고정)
- `data.yaml` — 클래스 75개. **번호를 절대 바꾸지 않는다**(라벨 데이터와 연결됨).
  추가는 맨 뒤(75번~)에만, 빼는 클래스는 번호를 비워 두고 라벨만 안 단다.
- `엘리베이터_객체인식_클래스_1차.xlsx` — 클래스별 한글 이름·위치·현장 별칭·근거 조항(별표22)·관련 불합격 내용(판정 지침 별표2)·우선순위, 3순위 10개('나중(3순위)' 시트).
- `make_kijun_map.py` — 위 두 파일로 앱의 `app/src/main/assets/kijun_class_map.json` (클래스 → 통합검색 검색어·근거 조항)을 만든다.

## 앱 연결

부품 박스를 누르면 검사기준 통합검색 사이트를 WebView 로 열어 그 부품의 대표 검색어로 검색합니다.

- 사이트 주소: `app/src/main/res/values/kijun_config.xml` 의 `kijun_base_url` **한 곳에만** 있음. 주소가 바뀌면 여기만 고친다.
- 검색어: 엑셀 '별칭·현장용어' 의 첫째 값. 다르게 쓰려면 `make_kijun_map.py` 의 `QUERY_OVERRIDE`.
- 예전 모델(5개 클래스)·저장된 검사 기록의 이름(`car_button_emerg` 등)은 `LEGACY` 로 새 클래스에 연결된다.
- 오프라인: 앱이 사이트 파일을 받아 폰 안에만 저장(`KijunSiteCache`). 이 저장소에는 사이트 복사본을 두지 않는다.

엑셀이나 data.yaml 을 새로 복사했으면:

```
D:\elevator_dataset\.venv\Scripts\python.exe yolo\make_kijun_map.py
```
