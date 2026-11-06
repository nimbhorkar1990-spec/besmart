from django.urls import path
from . import views

urlpatterns = [
    # Auth
    path('register/', views.register_view, name='register'),
    path('login/', views.login_view, name='login'),
    path('logout/', views.logout_view, name='logout'),
    path('forgot-password/', views.forgot_password_view, name='forgot_password'),
    path('reset-password/<str:username>/', views.reset_password_view, name='reset_password'),

    # Home / Dashboard
    path('', views.home_view, name='home'),
    path('post/create/', views.create_status_view, name='create_status'),
    path('post/edit/<int:post_id>/', views.edit_status_view, name='edit_status'),
    path('post/delete/<int:post_id>/', views.delete_status_view, name='delete_status'),
    path('post/like/<int:post_id>/', views.toggle_like_view, name='toggle_like'),
    path('post/comment/<int:post_id>/', views.add_comment_view, name='add_comment'),
    path('comment/delete/<int:comment_id>/', views.delete_comment_view, name='delete_comment'),

    # Profile
    path('profile/', views.profile_view, name='my_profile'),
    path('profile/edit/', views.edit_profile_view, name='edit_profile'),
    path('profile/rate/<str:username>/', views.rate_user_view, name='rate_user'),
    path('profile/<str:username>/', views.profile_view, name='profile'),
    path('settings/', views.settings_view, name='settings'),

    # Scrapbook
    path('scrapbook/', views.scrapbook_view, name='my_scrapbook'),
    path('scrapbook/<str:username>/', views.scrapbook_view, name='scrapbook'),
    path('scrapbook/delete/<int:scrap_id>/', views.delete_scrap_view, name='delete_scrap'),

    # Friends
    path('friends/', views.friends_list_view, name='friends_list'),
    path('friends/requests/', views.friend_requests_view, name='friend_requests'),
    path('friends/add/<str:username>/', views.send_friend_request_view, name='send_friend_request'),
    path('friends/accept/<int:request_id>/', views.accept_friend_request_view, name='accept_friend_request'),
    path('friends/reject/<int:request_id>/', views.reject_friend_request_view, name='reject_friend_request'),
    path('friends/remove/<str:username>/', views.remove_friend_view, name='remove_friend'),
    path('friends/<str:username>/', views.friends_list_view, name='user_friends'),

    # Communities
    path('communities/', views.communities_list_view, name='communities_list'),
    path('communities/create/', views.create_community_view, name='create_community'),
    path('communities/<int:community_id>/', views.community_detail_view, name='community_detail'),
    path('communities/<int:community_id>/join/', views.join_community_view, name='join_community'),
    path('communities/<int:community_id>/leave/', views.leave_community_view, name='leave_community'),
    path('communities/<int:community_id>/topic/new/', views.create_community_topic_view, name='create_community_topic'),
    path('communities/topic/<int:topic_id>/', views.community_topic_detail_view, name='community_topic_detail'),

    # Photos
    path('photos/', views.photos_list_view, name='my_photos'),
    path('photos/upload/', views.upload_photo_view, name='upload_photo'),
    path('photos/album/create/', views.create_album_view, name='create_album'),
    path('photos/album/<int:album_id>/', views.album_detail_view, name='album_detail'),
    path('photos/view/<int:photo_id>/', views.photo_detail_view, name='photo_detail'),
    path('photos/delete/<int:photo_id>/', views.delete_photo_view, name='delete_photo'),
    path('photos/<str:username>/', views.photos_list_view, name='photos_list'),

    # Messages / Chat
    path('messages/', views.messages_view, name='messages'),
    path('messages/<str:username>/', views.messages_view, name='chat_with_user'),
    path('messages/delete/<str:username>/', views.delete_conversation_view, name='delete_conversation'),
    path('api/messages/send/', views.send_message_api, name='api_send_message'),
    path('api/messages/get/<str:username>/', views.get_messages_api, name='api_get_messages'),

    # Fans & Visitors
    path('fans/', views.fans_list_view, name='my_fans'),
    path('fans/<str:username>/', views.fans_list_view, name='fans_list'),
    path('fans/toggle/<str:username>/', views.toggle_fan_view, name='toggle_fan'),
    path('visitors/', views.profile_visitors_view, name='visitors'),

    # Notifications & Search & Reports
    path('notifications/', views.notifications_view, name='notifications'),
    path('notifications/read/<int:notification_id>/', views.mark_notification_read_view, name='mark_notification_read'),
    path('notifications/read-all/', views.mark_all_notifications_read_view, name='mark_all_notifications_read'),
    path('search/', views.search_view, name='search'),
    path('report/', views.report_content_view, name='report_content'),

    # Admin Panel
    path('admin-dashboard/', views.admin_dashboard_view, name='admin_dashboard'),
    path('admin-dashboard/user/<int:user_id>/toggle/', views.admin_toggle_user_status, name='admin_toggle_user_status'),
    path('admin-dashboard/report/<int:report_id>/<str:action>/', views.admin_resolve_report, name='admin_resolve_report'),
]

