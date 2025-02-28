/*
 *  Copyright 2023 Collate.
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *  http://www.apache.org/licenses/LICENSE-2.0
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
import {
  CheckCircleTwoTone,
  ClockCircleOutlined,
  UserOutlined,
} from '@ant-design/icons';
import {
  Avatar,
  Button,
  Card,
  Collapse,
  Divider,
  Space,
  Typography,
} from 'antd';
import React, { useMemo } from 'react';
import { useTranslation } from 'react-i18next';
import AppState from '../../../AppState';
import { Transi18next } from '../../../utils/CommonUtils';
import { getRelativeTime } from '../../../utils/date-time/DateTimeUtils';
import { getEntityName } from '../../../utils/EntityUtils';
import BrandImage from '../../common/BrandImage/BrandImage';
import UserPopOverCard from '../../common/PopOverCard/UserPopOverCard';
import ProfilePicture from '../../common/ProfilePicture/ProfilePicture';
import AppLogo from '../AppLogo/AppLogo.component';
import './app-install-verify-card.less';
import { AppInstallVerifyCardProps } from './AppInstallVerifyCard.interface';

const AppInstallVerifyCard = ({
  appData,
  onCancel,
  onSave,
}: AppInstallVerifyCardProps) => {
  const { t } = useTranslation();
  const currentUser = useMemo(
    () => AppState.getCurrentUserDetails(),
    [AppState.userDetails, AppState.nonSecureUserDetails]
  );

  return (
    <div className="flex-center flex-col">
      <Space className="p-t-lg">
        <AppLogo appName={appData?.fullyQualifiedName ?? ''} />
        <Divider dashed className="w-44 app-card-divider">
          <CheckCircleTwoTone twoToneColor="#4CAF50" />
        </Divider>
        <Avatar
          className="app-marketplace-avatar flex-center bg-white border"
          icon={
            <BrandImage
              isMonoGram
              alt="OpenMetadata Logo"
              className="vertical-middle"
              dataTestId="image"
              height={56}
              width={56}
            />
          }
          size={100}
        />
      </Space>
      <Typography.Title className="m-t-md" level={5}>
        {t('label.authorize-app', {
          app: getEntityName(appData),
        })}
      </Typography.Title>
      <Card className="w-500 m-t-md">
        <Space size={12}>
          <UserPopOverCard
            userName={currentUser?.displayName ?? currentUser?.name ?? ''}>
            <span className="d-flex cursor-pointer" data-testid="authorAvatar">
              <ProfilePicture
                className="d-flex"
                id=""
                name={currentUser?.displayName ?? currentUser?.name ?? ''}
                type="circle"
                width="32"
              />
            </span>
          </UserPopOverCard>
          <div className="d-flex flex-col">
            <Typography.Text className="font-medium">
              <Transi18next
                i18nKey="label.application-by-developer"
                renderElement={
                  <a
                    href={appData?.developerUrl}
                    rel="noreferrer"
                    style={{ color: '#1890ff' }}
                    target="_blank"
                  />
                }
                values={{
                  dev: appData?.developer,
                  app: getEntityName(appData),
                }}
              />
            </Typography.Text>
            <Typography.Text className="text-grey-muted text-xs">
              {t('label.wants-to-access-your-account', {
                username: currentUser?.displayName ?? currentUser?.name,
              })}
            </Typography.Text>
          </div>
        </Space>

        <Collapse ghost className="w-full m-t-md" expandIconPosition="end">
          <Collapse.Panel
            header={` ${t('label.all-entity', {
              entity: t('label.metadata'),
            })}`}
            key="1"
          />
        </Collapse>

        <Divider />
        <div className="d-flex justify-end gap-2">
          <Button block data-testid="cancel" onClick={onCancel}>
            {t('label.cancel')}
          </Button>
          <Button
            block
            data-testid="save-button"
            key="save-btn"
            type="primary"
            onClick={onSave}>
            {t('label.configure')}
          </Button>
        </div>
      </Card>
      <Card className="w-500 m-t-md">
        <div className="d-flex items-center justify-between">
          <Space size={8}>
            <UserOutlined />
            <Typography.Text className="text-xs text-grey-muted">
              {t('label.developed-by-developer', {
                developer: appData?.developer,
              })}
            </Typography.Text>
          </Space>
          <Space size={8}>
            <ClockCircleOutlined />
            <Typography.Text className="text-xs text-grey-muted">
              {`${t('label.updated')} ${getRelativeTime(appData?.updatedAt)}`}
            </Typography.Text>
          </Space>
        </div>
      </Card>
    </div>
  );
};

export default AppInstallVerifyCard;
