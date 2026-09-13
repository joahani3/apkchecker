# History

- [2026-09-13] 책장(BOOKSHELF.html) 화면 추가 및 앱-PC 간 비공개 동기화 연동
- 화면/기능 ID: SCR-03 / FN-07
- 작업 목적 (Why): PC(~/coding)에서 shelf.py가 생성하는 저장소 현황 책장(BOOKSHELF.html)을 안드로이드 앱에서도 바로 보고 싶다는 요청. BOOKSHELF.html에는 비공개 저장소 이름과 최근 커밋 메시지가 담겨 있어 공개 GitHub Pages로 배포하면 인터넷에 노출되므로, 대신 비공개 저장소(coding-bookshelf)에 두고 앱이 기존 GitHub PAT 로그인 토큰으로 비공개 조회하도록 함
- 주요 변경 (What): GitHubApi에 raw content 조회용 getRawFileContent() 추가(Accept: application/vnd.github.raw), NetworkModule 인터셉터가 호출별 Accept 헤더를 존중하도록 수정, GitHubRepository.fetchBookshelfHtml() 추가, 신규 BookshelfScreen/BookshelfViewModel(WebView 렌더링, 파일 캐시 경유로 대용량 HTML 로드), 상단 ⋮ 메뉴에 '책장 보기' 항목과 NavGraph 'bookshelf' 라우트 추가, versionName 3.11→3.12(versionCode 13). 별도로 ~/.claude/scripts/shelf.py에 sync_to_github()를 추가해 로컬 BOOKSHELF.html 생성 후 gh CLI로 coding-bookshelf/index.html에 자동 동기화하도록 확장(코드리뷰 대상 아님, 앱 저장소 밖 스크립트).
- 연계 영향 및 개선 과제 (TODO): WebView는 파일 스킴(file://)으로 로드하며 mermaid.js가 인라인 포함되어 외부 네트워크 요청은 없음. coding-bookshelf 저장소 접근 권한이 없는 GitHub 계정으로 로그인하면 책장 조회가 실패하므로(현재는 단일 사용자 개인 앱이라 owner를 로그인 계정으로 가정), 다계정 지원 시 저장소 이름을 설정 가능하게 바꿀 필요 있음.

