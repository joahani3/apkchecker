# 화면/기능 구조 (ARCHITECTURE)

## 변경 사항 (이번 실행)

- 추가: SCR-03, FN-07

## 1. 메뉴/화면 계층 트리

- **SCR-01** 로그인
- **SCR-02** 저장소 목록
- **SCR-03** 책장

## 2. 화면-기능 연계 상세 표

| 화면 ID | 화면명 | 기능 ID | 연동 데이터/API | 개발 목적 (Why) |
|---|---|---|---|---|
| SCR-01 | 로그인 | FN-01 | GitHubRepository.login(token) | GitHub Personal Access Token으로 인증하여 내 저장소와 star/watch 저장소의 릴리즈를 확인할 수 있게 함 |
| SCR-02 | 저장소 목록 | FN-02 | GitHubRepository.refreshRepos() | 로그인한 사용자의 소유/Star/Watch 저장소별 최신 릴리즈와 설치 상태를 보여줌 |
| SCR-02 | 저장소 목록 | FN-03 | ApkDownloader.download()/install(), InstalledAppChecker.getInstalledPackageInfo() | 새 릴리즈 또는 업데이트가 있는 저장소의 APK 에셋을 다운로드하고 바로 설치할 수 있게 함 |
| SCR-02 | 저장소 목록 | FN-04 | GitHubRepository.hideRepo()/unhideRepo()/getHiddenRepos() | 관심 없는 저장소를 관리 목록에서 숨기고, 필요 시 숨긴 저장소를 다시 복원할 수 있게 함 |
| SCR-02 | 저장소 목록 | FN-05 | BackupManager.createBackup()/restoreBackup() | 감시 대상 저장소 설정을 텍스트 파일로 백업하고 복구할 수 있게 함 |
| SCR-02 | 저장소 목록 | FN-06 | GitHubRepository.logout() | GitHub 인증 토큰을 지우고 로그인 화면으로 돌아가게 함 |
| SCR-03 | 책장 | FN-07 | GitHubRepository.fetchBookshelfHtml() → GitHubApi.getRawFileContent(owner, "coding-bookshelf", "index.html") (Accept: application/vnd.github.raw), WebView 렌더링 | PC(~/coding)에서 shelf.py가 만드는 저장소 현황 책장(BOOKSHELF.html)을 앱에서도 바로 볼 수 있게 함. 비공개 저장소 이름/커밋 이력이 담겨 있어 공개 GitHub Pages 대신 비공개 저장소(coding-bookshelf)에 두고, 기존 GitHub PAT 로그인 토큰으로 Contents API를 통해 비공개로 조회함 |

## 3. 지식 그래프 (Mermaid)

```mermaid
graph LR
    subgraph Screens ["화면 계층"]
        SCR-01["[SCR-01] 로그인"]
        SCR-02["[SCR-02] 저장소 목록"]
        SCR-03["[SCR-03] 책장"]
    end

    subgraph Features ["기능"]
        FN-01["(FN-01) GitHub PAT 로그인"]
        FN-02["(FN-02) 저장소/릴리즈 목록 조회 및 새로고침"]
        FN-03["(FN-03) APK 다운로드 및 설치"]
        FN-04["(FN-04) 저장소 숨기기/복원 관리"]
        FN-05["(FN-05) 설정 백업/복구"]
        FN-06["(FN-06) 로그아웃"]
        FN-07["(FN-07) 책장 조회 (레포 현황 책장 보기)"]
    end

    subgraph DataService ["백그라운드 &amp; 저장소"]
        FN-01_DATA[("GitHubRepository.login(token)")]
        FN-02_DATA[("GitHubRepository.refreshRepos()")]
        FN-03_DATA[("ApkDownloader.download()/install(), InstalledAppChecker.getInstalledPackageInfo()")]
        FN-04_DATA[("GitHubRepository.hideRepo()/unhideRepo()/getHiddenRepos()")]
        FN-05_DATA[["BackupManager.createBackup()/restoreBackup()"]]
        FN-06_DATA[("GitHubRepository.logout()")]
        FN-07_DATA[("GitHubRepository.fetchBookshelfHtml() → GitHubApi.getRawFileContent(owner, &quot;coding-bookshelf&quot;, &quot;index.html&quot;) (Accept: application/vnd.github.raw), WebView 렌더링")]
    end

    SCR-01 --> FN-01
    FN-01 --> FN-01_DATA
    SCR-02 --> FN-02
    FN-02 --> FN-02_DATA
    SCR-02 --> FN-03
    FN-03 --> FN-03_DATA
    SCR-02 --> FN-04
    FN-04 --> FN-04_DATA
    SCR-02 --> FN-05
    FN-05 --> FN-05_DATA
    SCR-02 --> FN-06
    FN-06 --> FN-06_DATA
    SCR-03 --> FN-07
    FN-07 --> FN-07_DATA
```
