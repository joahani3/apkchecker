# History

- [2026-09-13] 책장(BOOKSHELF.html) 화면 추가 및 앱-PC 간 비공개 동기화 연동
- 화면/기능 ID: SCR-03 / FN-07
- 작업 목적 (Why): PC(~/coding)에서 shelf.py가 생성하는 저장소 현황 책장(BOOKSHELF.html)을 안드로이드 앱에서도 바로 보고 싶다는 요청. BOOKSHELF.html에는 비공개 저장소 이름과 최근 커밋 메시지가 담겨 있어 공개 GitHub Pages로 배포하면 인터넷에 노출되므로, 대신 비공개 저장소(coding-bookshelf)에 두고 앱이 기존 GitHub PAT 로그인 토큰으로 비공개 조회하도록 함
- 주요 변경 (What): GitHubApi에 raw content 조회용 getRawFileContent() 추가(Accept: application/vnd.github.raw), NetworkModule 인터셉터가 호출별 Accept 헤더를 존중하도록 수정, GitHubRepository.fetchBookshelfHtml() 추가, 신규 BookshelfScreen/BookshelfViewModel(WebView 렌더링, 파일 캐시 경유로 대용량 HTML 로드), 상단 ⋮ 메뉴에 '책장 보기' 항목과 NavGraph 'bookshelf' 라우트 추가, versionName 3.11→3.12(versionCode 13). 별도로 ~/.claude/scripts/shelf.py에 sync_to_github()를 추가해 로컬 BOOKSHELF.html 생성 후 gh CLI로 coding-bookshelf/index.html에 자동 동기화하도록 확장(코드리뷰 대상 아님, 앱 저장소 밖 스크립트).
- 연계 영향 및 개선 과제 (TODO): WebView는 파일 스킴(file://)으로 로드하며 mermaid.js가 인라인 포함되어 외부 네트워크 요청은 없음. coding-bookshelf 저장소 접근 권한이 없는 GitHub 계정으로 로그인하면 책장 조회가 실패하므로(현재는 단일 사용자 개인 앱이라 owner를 로그인 계정으로 가정), 다계정 지원 시 저장소 이름을 설정 가능하게 바꿀 필요 있음.

- [2026-09-13] 책장 화면 net::ERR_ACCESS_DENIED 수정 (file:// → WebViewAssetLoader)
- 화면/기능 ID: SCR-03 / FN-07
- 작업 목적 (Why): 책장 보기 메뉴를 눌렀을 때 WebView가 file:// 스킴으로 캐시된 HTML을 로드하다 net::ERR_ACCESS_DENIED로 실패하는 것을 사용자가 실기기에서 확인. 최신 WebView(Chromium)가 앱 전용 저장소라도 file:// URL 로드를 차단하는 경우가 있어 발생
- 주요 변경 (What): androidx.webkit:webkit 의존성 추가, BookshelfScreen이 WebViewAssetLoader.InternalStoragePathHandler로 cacheDir/bookshelf를 https://appassets.androidplatform.net/bookshelf/ 가상 도메인에 매핑하고 그 URL을 로드하도록 변경 (WebViewClient.shouldInterceptRequest에서 위임). versionName 3.12→3.13(versionCode 14)
- 연계 영향 및 개선 과제 (TODO): 실기기에서 책장 보기가 정상 로드되는지 재확인 필요. WebViewAssetLoader는 캐시 디렉터리 전체를 가상 도메인 아래 노출하므로, 추후 다른 파일을 같은 cacheDir에 두지 않도록 주의.

- [2026-09-20] 첫 화면 진입 시마다 저장소 목록 새로고침; 3.14 빌드/릴리스
- 화면/기능 ID: SCR-02 / FN-02
- 작업 목적 (Why): 앱을 열거나 다른 화면에서 돌아올 때마다 최신 릴리즈 정보를 보여주기 위함 (기존에는 ViewModel 생성 시 1회만 갱신)
- 주요 변경 (What): RepoListViewModel의 init{refresh()} 제거, RepoListScreen에 LifecycleEventEffect(ON_START)로 refresh() 호출 추가. versionCode 15 / versionName 3.14로 올리고 push해 CI가 APK 릴리스 생성
- 연계 영향 및 개선 과제 (TODO): 화면 회전(액티비티 재생성) 시에도 ON_START로 재갱신될 수 있음. Play Console 버전 표시(WIP)는 이번 커밋에서 제외됨. CI 워크플로 제목을 'apk vX.Y'로 자동화 가능 (workflow 스코프 확보됨)

- [2026-09-26] APK 다운로드 진행률 표시; 3.15 빌드/릴리스
- 화면/기능 ID: SCR-02 / FN-03
- 작업 목적 (Why): APK 다운로드를 누른 뒤 진행 상황을 앱 안에서 확인할 수 없어 다운로드 중인지 알기 어려웠음
- 주요 변경 (What): ApkDownloader에 DownloadProgress/onProgress 콜백 추가(DownloadManager 받은/전체 바이트 폴링), RepoListScreen 카드 하단에 LinearProgressIndicator + 퍼센트/용량 표시, 다운로드 중 APK 칩 비활성화 및 중복 다운로드 방지. versionCode 16 / versionName 3.15
- 연계 영향 및 개선 과제 (TODO): 화면을 벗어나면(컴포지션 해제) 진행률 추적이 끊김 — 다운로드 자체는 DownloadManager가 계속함. Play Console 버전 표시(WIP)는 이번 커밋에서도 제외됨

- [2026-09-28] 3.16 빌드/릴리스 (버전만 올림)
- 화면/기능 ID: 미상 / 미상
- 작업 목적 (Why): GitHub Releases 전용 배포 흐름에 따라 새 APK 릴리스 요청
- 주요 변경 (What): versionCode 17 / versionName 3.16으로 올리고 push해 CI가 APK 릴리스 생성. 앱 코드는 3.15와 동일
- 연계 영향 및 개선 과제 (TODO): Play Console 버전 표시(WIP)는 서비스 계정 키를 런타임에 비공개 저장소에서 받아오는 구조라 이번 커밋에서도 제외하고 git stash로 보관함. 공개 릴리스에 포함할지 결정 필요

- [2026-09-28] 3.17 로컬 빌드 후 GitHub Release 수동 업로드
- 화면/기능 ID: 미상 / 미상
- 작업 목적 (Why): CI 대신 로컬에서 APK를 빌드해 직접 업로드하길 원함
- 주요 변경 (What): versionCode 18 / versionName 3.17로 올리고 [skip ci] 커밋으로 CI 중복 릴리스 방지. 로컬 assembleDebug APK를 gh release create로 v3.17 태그(제목 'apk v3.17')에 업로드. 앱 코드는 3.15/3.16과 동일
- 연계 영향 및 개선 과제 (TODO): 수동 릴리스 태그는 v3.17 형식이라 CI의 apk-N 태그와 섞임. Play Console 버전 표시(WIP)는 여전히 제외되어 stash에서 작업 트리로 복원됨

- [2026-09-28] 공개 저장소도 최신 릴리스에 APK가 있으면 목록에 표시; 3.18 로컬 빌드/릴리스
- 화면/기능 ID: SCR-02 / FN-02
- 작업 목적 (Why): apkchecker 저장소를 공개로 바꾼 뒤 3.10의 비공개 전용 필터 때문에 앱 자신의 저장소가 저장소 목록에서 사라짐
- 주요 변경 (What): GitHubRepository.refreshRepos에서 repo.private 필터 제거, buildRepoRelease가 공개 저장소는 최신 릴리스에 APK 에셋이 없으면 null을 반환하고 filterNotNull로 제외. versionCode 19 / versionName 3.18, 로컬 assembleDebug APK를 v3.18 릴리스로 업로드([skip ci])
- 연계 영향 및 개선 과제 (TODO): 공개 -app 저장소(SMSBridge-app, PDFmasking-app 등 11개)도 APK 릴리스가 있어 목록에 새로 나타남 — 비공개 원본 저장소와 중복될 수 있어 숨기기 또는 추가 규칙 필요. 공개 저장소마다 latest release 요청이 1회씩 늘어남. Play Console 버전 표시(WIP)는 이번에도 제외

- [2026-09-28] -app 배포용 미러 저장소를 목록에서 제외; 3.19 로컬 빌드/릴리스
- 화면/기능 ID: SCR-02 / FN-02
- 작업 목적 (Why): 3.18에서 APK가 있는 공개 저장소를 표시하면서, 비공개 원본의 공개 배포 미러인 -app 저장소 11개가 함께 나타나 같은 앱이 중복될 수 있었음
- 주요 변경 (What): GitHubRepository에 isDistributionMirror(이름이 -app으로 끝남, 대소문자 무시) 추가, refreshRepos 필터에서 제외. 비공개 -app 저장소는 없음을 확인. versionCode 20 / versionName 3.19, 로컬 APK를 v3.19 릴리스로 업로드([skip ci])
- 연계 영향 및 개선 과제 (TODO): 이름 규칙 기반이라 -app이 아닌 미러 저장소는 걸러지지 않음. Play Console 버전 표시(WIP)는 이번에도 제외

- [2026-09-30] 저장소 목록에 NEW/UPDATE/ALL 탭과 책장 버튼 배치를 추가하고, 오늘 푸시된 저장소 상단 노출 및 Play Console 트랙 버전 연동을 커밋
- 화면/기능 ID: SCR-02 / FN-02
- 작업 목적 (Why): 설치 필요(NEW)/업데이트 필요(UPDATE) 저장소를 한눈에 구분해서 보고 싶다는 요청과, Play Console에 배포된 실제 버전을 앱 안에서 바로 확인하기 위함
- 주요 변경 (What): RepoListScreen에 NEW/UPDATE/ALL 탭 UI 추가(대상 있으면 'NEW(3)' 형식으로 개수 표시), 책장 버튼을 탭 행 우측으로 이동; GithubRepo에 pushed_at 필드를 추가해 오늘 푸시된 저장소를 정렬 최상단으로 올리고 카드에 '작업필요' 배지 표시; PlayConsoleRepository/AndroidPublisherApi/GoogleApiModule/JwtSigner 등 Play Console API 연동을 커밋해 저장소 카드에 Play 비공개 테스트/프로덕션 버전 노출; versionCode 21 / versionName 3.20으로 bump
- 연계 영향 및 개선 과제 (TODO): release-apk.yml이 push 시 자동으로 debug APK를 빌드해 GitHub Release를 생성하므로, 생성된 릴리즈 제목을 'apk v3.20' 형식으로 수동 변경 필요(워크플로 파일 수정 권한 없음); Play Console 서비스 계정 키 로드 및 트랙 버전 조회가 실기기에서 정상 동작하는지 확인 필요

- [2026-09-30] 저장소 목록 탭을 NEW/UPDATE에서 APK/TODAY로 개편하고, 오늘 push됐지만 아직 APK 빌드가 없는 저장소를 우선 노출
- 화면/기능 ID: SCR-02 / FN-02
- 작업 목적 (Why): 신규/업데이트 대상은 하나의 APK 탭으로 합치고, 오늘 소스가 바뀐 저장소 중 아직 빌드가 안 된 것을 먼저 확인하고 싶다는 요청
- 주요 변경 (What): RepoListTab을 NEW/UPDATE/ALL에서 APK/TODAY/ALL로 변경. APK 탭은 기존 NOT_INSTALLED+UPDATE_AVAILABLE을 합친 목록으로 동작. TODAY 탭은 repo.pushedToday인 저장소만 모아, 1순위(오늘 push했지만 releaseToday 없음) 다음에 2순위(오늘 push+오늘 release도 있음) 순으로 정렬. RepoRelease/GitHubRepository에 releaseToday(release.publishedAt이 오늘인지) 필드 추가. versionCode 22 / versionName 3.21로 bump
- 연계 영향 및 개선 과제 (TODO): release-apk.yml push 트리거로 CI가 debug APK를 빌드해 GitHub Release를 생성하면, 릴리즈 제목을 'apk v3.21' 형식으로 수동 변경 필요(워크플로 파일 수정 권한 없음)

