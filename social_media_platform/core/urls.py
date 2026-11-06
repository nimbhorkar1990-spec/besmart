from django.urls import path
from . import views

urlpatterns = [
    path('', lambda request: views.redirect('feed') if request.user.is_authenticated else views.redirect('login'), name='home'),
    path('register/', views.register_view, name='register'),
    path('login/', views.login_view, name='login'),
    path('logout/', views.logout_view, name='logout'),
    path('forgot-password/', views.forgot_password_view, name='forgot_password'),
    path('reset-password/', views.reset_password_view, name='reset_password'),

    # Feed & Posts
    path('feed/', views.feed_view, name='feed'),
    path('post/create/', views.create_post_view, name='create_post'),
    path('post/<int:post_id>/', views.post_detail_view, name='post_detail'),
    path('post/<int:post_id>/edit/', views.edit_post_view, name='edit_post'),
    path('post/<int:post_id>/delete/', views.delete_post_view, name='delete_post'),
    path('post/<int:post_id>/like/', views.toggle_like_view, name='toggle_like'),
    path('post/<int:post_id>/save/', views.toggle_save_view, name='toggle_save'),
    path('post/<int:post_id>/comment/', views.add_comment_view, name='add_comment'),
    path('comment/<int:comment_id>/delete/', views.delete_comment_view, name='delete_comment'),
    path('post/<int:post_id>/share/', views.share_post_view, name='share_post'),

    # Profile
    path('profile/<str:username>/', views.profile_view, name='profile'),
    path('profile-edit/', views.edit_profile_view, name='edit_profile'),
    path('account/deactivate/', views.deactivate_account_view, name='deactivate_account'),

    # Network & Connections
    path('network/', views.network_view, name='network'),
    path('connect/send/<int:user_id>/', views.send_connection_view, name='send_connection'),
    path('connect/accept/<int:connection_id>/', views.accept_connection_view, name='accept_connection'),
    path('connect/reject/<int:connection_id>/', views.reject_connection_view, name='reject_connection'),
    path('connect/remove/<int:user_id>/', views.remove_connection_view, name='remove_connection'),
    path('follow/<int:user_id>/', views.toggle_follow_view, name='toggle_follow'),
    path('block/<int:user_id>/', views.block_user_view, name='block_user'),
    path('unblock/<int:user_id>/', views.unblock_user_view, name='unblock_user'),

    # Messages
    path('messages/', views.messages_view, name='messages'),
    path('messages/<str:username>/', views.messages_view, name='messages_thread'),
    path('api/messages/send/<str:username>/', views.send_message_api, name='send_message_api'),
    path('api/messages/get/<str:username>/', views.get_messages_api, name='get_messages_api'),

    # Notifications
    path('notifications/', views.notifications_view, name='notifications'),
    path('notifications/<int:notification_id>/read/', views.mark_notification_read_view, name='mark_notification_read'),
    path('notifications/read-all/', views.mark_all_notifications_read_view, name='mark_all_notifications_read'),

    # Search
    path('search/', views.search_view, name='search'),

    # Groups
    path('groups/', views.groups_list_view, name='groups_list'),
    path('groups/create/', views.create_group_view, name='create_group'),
    path('groups/<slug:slug>/', views.group_detail_view, name='group_detail'),
    path('groups/<slug:slug>/join/', views.join_group_view, name='join_group'),

    # Companies
    path('companies/', views.companies_list_view, name='companies_list'),
    path('companies/create/', views.create_company_view, name='create_company'),
    path('companies/<slug:slug>/', views.company_detail_view, name='company_detail'),
    path('companies/<slug:slug>/follow/', views.toggle_follow_company_view, name='toggle_follow_company'),

    # Admin Dashboard & Moderation
    path('admin-dashboard/', views.admin_dashboard_view, name='admin_dashboard'),
    path('report/', views.report_content_view, name='report_content'),
    path('admin-dashboard/resolve/<int:report_id>/', views.admin_resolve_report_view, name='admin_resolve_report'),
    path('admin-dashboard/user-status/<int:user_id>/', views.admin_toggle_user_status_view, name='admin_toggle_user_status'),
]

