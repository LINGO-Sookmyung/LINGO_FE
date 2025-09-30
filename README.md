# LINGO_FE


## INTRODUCE
공증문서 번역 및 문서 변환 자동화 서비스 "LINGO" 프론트엔드 레포입니다.


## MEMBER
| 김민지<br/>([@minjiwkim](https://github.com/minjiwkim)) |
| :---: |
| <img width="200" alt="Image" src="https://github.com/user-attachments/assets/4ddbab6f-b7aa-4eeb-a1a7-f3ca8e9ee4e4" /> |


## INSTALLATION & SETUP
1. 저장소를 클론합니다.
   ```bash
   git clone https://github.com/LINGO-Sookmyung/LINGO_FE.git
   ```
2. Android Studio에서 프로젝트를 엽니다.
3. 에뮬레이터 또는 실제 기기에서 빌드 후 실행합니다.


## TECH STACKS
| Category     | Stack                          |
|--------------|-------------------------------|
| Language     | Kotlin                         |
| IDE          | Android Studio Meerkat         |
| Networking   | Retrofit, OkHttp               |


## PROJECT STRUCTURE
```bash
.
├── core/
│   ├── di/
│   │   └── ServiceLocator.kt                         # 의존성 주입 관리
│   ├── model/
│   │   ├── ApiError.kt                               # API 에러 모델 정의
│   │   └── CallResult.kt                             # API 호출 결과 wrapper
│   └── network/
│       ├── ApiService.kt                             # Retrofit API 인터페이스
│       ├── AuthInterceptor.kt                        # 인증 토큰 인터셉터
│       └── RetrofitClient.kt                         # Retrofit 클라이언트 설정
├── data/
│   ├── local/
│   │   ├── AgreementPrefs.kt                         # 약관 동의 로컬 저장소
│   │   ├── MyDocumentsStore.kt                       # 내 문서 로컬 저장소
│   │   └── TokenManager.kt                           # 토큰 관리 유틸
│   ├── repository/
│   │   ├── AuthRepository.kt                         # 인증 관련 데이터 소스
│   │   ├── DocumentRepository.kt                     # 문서 관련 데이터 소스
│   │   └── UploadRepository.kt                       # 파일 업로드 데이터 소스
│   └── model/
│       ├── storage/
│       │   └── S3Presigned.kt                        # S3 Presigned URL 모델
│       ├── auth/
│       │   ├── Reissue.kt                            # 토큰 재발급 모델
│       │   └── SignupModels.kt                       # 회원가입 요청/응답 모델
│       └── document/
│           ├── GenerateDocModels.kt                  # 문서 생성 요청/응답 모델
│           ├── RegisterOriginalDocument.kt           # 원본 문서 등록 모델
│           └── TranslatedDocumentDto.kt              # 번역 문서 DTO
└── ui/
    ├── base/
    │   └── BaseActivity.kt                           # 상단 네비게이션 바
    ├── start/
    │   ├── AgreementActivity.kt                      # 약관 동의 화면
    │   ├── SignupActivity.kt                         # 회원가입 화면
    │   └── StartActivity.kt                          # 시작 화면(회원가입/로그인/로그인 없이 이용 선택)
    ├── login/
    │   ├── LoginActivity.kt                          # 로그인 화면
    │   └── find/
    │       ├── FindEmailActivity.kt                  # 이메일 찾기
    │       ├── FindEmailResultActivity.kt            # 이메일 찾기 결과
    │       ├── FindPasswordActivity.kt               # 비밀번호 찾기
    │       ├── FindPasswordCodeActivity.kt           # 이메일로 전송된 코드 입력
    │       └── FindPasswordResultActivity.kt         # 임시 비밀번호 발급
    └── main/
        ├── MainActivity.kt                           # 메인 화면
        ├── mypage/
        │   ├── ChangePasswordCurrentActivity.kt      # 비밀번호 변경 - 현재 비밀번호 입력
        │   ├── ChangePasswordNewActivity.kt          # 비밀번호 변경 - 새 비밀번호 입력
        │   ├── DocumentsActivity.kt                  # 내 문서함
        │   └── MyPageActivity.kt                     # 마이페이지 화면
        └── translation/
            ├── TranslationDocActivity.kt             # 번역 문서 기본정보 입력
            ├── TranslationDocImagesActivity.kt       # 번역 문서 이미지 선택
            ├── TranslationFamilyResultActivity.kt    # 가족관계증명서 번역 결과 확인/수정
            ├── TranslationInProgressActivity.kt      # 번역 진행
            ├── TranslationResultActivity.kt          # 번역 결과 확인/수정(가족관계증명서의 경우 여기서 영문 이름만 확인/수정, 다음 버튼을 누르면 TranslationFamilyResultActivity로 넘어감)
            ├── adapter/
            │   └── TranslationPhotoAdapter.kt        # 번역 이미지 리스트 어댑터
            ├── camera/
            │   ├── CameraCaptureActivity.kt          # 문서 촬영 화면
            │   ├── CameraOverlayView.kt              # 카메라 가이드 오버레이 뷰
            │   ├── PhotoCaptureGuideActivity.kt      # 촬영 가이드
            │   └── PhotoReviewActivity.kt            # 촬영 후 사진 검토
            └── document/
                ├── DocumentGeneratingActivity.kt     # 번역 문서 생성
                └── DocumentPreviewActivity.kt        # 생성된 문서 다운로드
```

